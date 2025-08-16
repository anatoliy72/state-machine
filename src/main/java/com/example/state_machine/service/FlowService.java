package com.example.state_machine.service;

import com.example.state_machine.config.StateMachineConfig;
import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessHistory;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import com.example.state_machine.repository.ProcessHistoryRepository;
import com.example.state_machine.repository.ProcessInstanceRepository;
import com.example.state_machine.service.advance.PreconditionRegistry;
import com.example.state_machine.service.advance.PreconditionsNotMetException;
import com.example.state_machine.service.advance.StepPlan;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Orchestrates the minor account opening workflow using Spring State Machine and MongoDB persistence.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Create and fetch {@link ProcessInstance} records</li>
 *   <li>Advance state machines via client-driven {@code /event} and server-driven {@code /advance}</li>
 *   <li>Validate domain preconditions before transitions</li>
 *   <li>Persist state snapshots and a detailed transition history in MongoDB</li>
 *   <li>Restore the state machine context from an in-memory persister or the DB-stored state</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowService {

    private final ProcessInstanceRepository repository;
    private final ProcessHistoryRepository historyRepository;

    private final StateMachineFactory<ProcessState, ProcessEvent> stateMachineFactory;
    private final StateMachinePersist<ProcessState, ProcessEvent, String> stateMachinePersist;

    private final StepPlan stepPlan;

    /**
     * Registry of preconditions checked prior to transitions.
     * May be {@code null} in unit tests that use {@code @InjectMocks}.
     */
    private final PreconditionRegistry preconditions;

    // ===================== CRUD =====================

    /**
     * Starts a new process instance in {@link ProcessState#STARTED}.
     *
     * @param clientId    unique client identifier
     * @param type        process type (flow variant)
     * @param initialData initial variables map (optional)
     * @return persisted {@link ProcessInstance}
     * @throws IllegalArgumentException when {@code clientId} is blank or {@code type} is {@code null}
     */
    @Transactional
    public ProcessInstance startProcess(String clientId, ProcessType type, Map<String, Object> initialData) {
        Assert.hasText(clientId, "clientId must not be blank");
        Assert.notNull(type, "type must not be null");

        Map<String, Object> vars = (initialData != null) ? new HashMap<>(initialData) : new HashMap<>();

        ProcessInstance instance = ProcessInstance.builder()
                .clientId(clientId)
                .type(type)
                .state(ProcessState.STARTED)
                .variables(vars)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        instance = repository.save(instance);
        // initial history snapshot
        saveHistory(instance.getId(), null, ProcessState.STARTED, null, instance.getVariables());
        return instance;
    }

    /**
     * Retrieves a process by its identifier.
     *
     * @param processId process identifier
     * @return existing {@link ProcessInstance}
     * @throws NoSuchElementException when the process is not found
     */
    @Transactional(readOnly = true)
    public ProcessInstance getProcess(String processId) {
        return repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found"));
    }

    /**
     * Merges the provided variables into the process instance without changing its state.
     * Produces a history record with the updated variables snapshot.
     *
     * @param processId process identifier
     * @param updates   variables to merge (may be empty)
     * @return updated {@link ProcessInstance}
     * @throws NoSuchElementException when the process is not found
     */
    @Transactional
    public ProcessInstance updateVariables(String processId, Map<String, Object> updates) {
        ProcessInstance instance = repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found"));

        if (updates != null && !updates.isEmpty()) {
            if (instance.getVariables() == null || !(instance.getVariables() instanceof HashMap)) {
                instance.setVariables(new HashMap<>(instance.getVariables() == null ? Map.of() : instance.getVariables()));
            }
            instance.getVariables().putAll(updates);
        }
        instance.setUpdatedAt(Instant.now());
        repository.save(instance);
        // history entry for data update without a state change
        saveHistory(instance.getId(), instance.getState(), instance.getState(), null, updates);
        return instance;
    }

    /**
     * Returns the chronological history of a process transitions and snapshots.
     *
     * @param processId process identifier
     * @return list of {@link ProcessHistory} ordered by timestamp (ascending)
     */
    @Transactional(readOnly = true)
    public List<ProcessHistory> getHistory(String processId) {
        return historyRepository.findByProcessIdOrderByTimestampAsc(processId);
    }

    // ===================== EVENTS =====================

    /**
     * Handles a client-driven event by sending it to the state machine after validating preconditions.
     * Also persists the new state, updates variables, saves history, and writes the SM context to the persister.
     *
     * @param processId process identifier
     * @param event     event to send
     * @param data      variables to merge into the process (may be empty)
     * @return updated {@link ProcessInstance}
     * @throws NoSuchElementException        when the process is not found
     * @throws PreconditionsNotMetException  when one or more preconditions are violated
     * @throws IllegalStateException         when the event is not accepted by the state machine
     */
    @Transactional
    public ProcessInstance handleEvent(String processId, ProcessEvent event, Map<String, Object> data) {
        ProcessInstance instance = repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));

        // Optional: also enforce preconditions on /event to prevent bypassing /advance
        if (preconditions != null) {
            var errs = preconditions.validateAll(instance, event, data != null ? data : Map.of());
            if (!errs.isEmpty()) {
                throw new PreconditionsNotMetException(instance.getState(), errs);
            }
        }

        StateMachine<ProcessState, ProcessEvent> sm = initStateMachine(instance, data);

        ProcessState prev = instance.getState();
        boolean accepted = sm.sendEvent(event);
        if (!accepted) {
            log.warn("Event not accepted. id={}, state={}, event={}", processId, prev, event);
            throw new IllegalStateException("Event not accepted: " + event);
        }

        // resolve new state
        State<ProcessState, ProcessEvent> current = sm.getState();
        ProcessState newState = (current != null ? current.getId() : prev);

        // merge variables
        if (data != null && !data.isEmpty()) {
            if (instance.getVariables() == null || !(instance.getVariables() instanceof HashMap)) {
                instance.setVariables(new HashMap<>(instance.getVariables() == null ? Map.of() : instance.getVariables()));
            }
            instance.getVariables().putAll(data);
        }

        instance.setState(newState);
        instance.setUpdatedAt(Instant.now());
        repository.save(instance);

        // persist SM context (best effort)
        try {
            stateMachinePersist.write(
                    new DefaultStateMachineContext<>(newState, null, null, null),
                    processId
            );
        } catch (Exception e) {
            log.debug("Persist SM context failed (ignored). id={}", processId, e);
        }

        // add transition history
        saveHistory(processId, prev, newState, event, data);
        return instance;
    }

    /**
     * Advances the process according to the server-driven {@link StepPlan}.
     * The next event is derived from (type, currentState), validated, and then handled as a normal event.
     *
     * @param processId process identifier
     * @param data      variables to merge for this step (may be empty)
     * @return updated {@link ProcessInstance}
     * @throws NoSuchElementException       when the process is not found
     * @throws IllegalStateException        when no next step is defined for the current state
     * @throws PreconditionsNotMetException when preconditions fail
     */
    @Transactional
    public ProcessInstance advance(String processId, Map<String, Object> data) {
        ProcessInstance pi = repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));

        ProcessEvent next = stepPlan.next(pi.getType(), pi.getState())
                .orElseThrow(() -> new IllegalStateException("No next step for state " + pi.getState()));

        if (preconditions != null) {
            var errors = preconditions.validateAll(pi, next, data != null ? data : Map.of());
            if (!errors.isEmpty()) {
                throw new PreconditionsNotMetException(pi.getState(), errors);
            }
        }

        return handleEvent(processId, next, data != null ? data : Map.of());
    }

    /**
     * Advances the process by sending the specified event to the state machine.
     * The event must be valid for the current state of the process.
     *
     * @param id    process identifier
     * @param event event to send
     * @param data  variables to merge into the process (may be empty)
     * @return updated {@link ProcessInstance}
     * @throws NoSuchElementException        when the process is not found
     * @throws IllegalStateException         when the event is not valid for the current state
     * @throws PreconditionsNotMetException  when one or more preconditions are violated
     */
    @Transactional
    public ProcessInstance advanceProcess(String id, ProcessEvent event, Map<String, Object> data) {
        ProcessInstance instance = getProcess(id);

        // Get current state machine
        StateMachine<ProcessState, ProcessEvent> sm = getStateMachine(instance);

        // Update variables if provided
        if (data != null && !data.isEmpty()) {
            sm.getExtendedState().getVariables().putAll(data);
        }

        // Send event
        boolean success = sm.sendEvent(event);
        if (!success) {
            throw new IllegalStateException("No next step for state " + instance.getState());
        }

        // Get current state after transition
        State<ProcessState, ProcessEvent> currentState = sm.getState();
        if (currentState == null || currentState.getId() == null) {
            throw new IllegalStateException("Invalid state after transition");
        }

        // Update instance with new state
        instance.setState(ProcessState.valueOf(currentState.getId().name()));
        // Создаем новую HashMap с правильными типами
        Map<String, Object> newVariables = new HashMap<>();
        sm.getExtendedState().getVariables().forEach((k, v) -> newVariables.put(k.toString(), v));
        instance.setVariables(newVariables);
        instance.setUpdatedAt(Instant.now());

        // Save and return updated instance
        return repository.save(instance);
    }

    // ===================== helpers =====================

    /**
     * Initializes a {@link StateMachine} for the given process by attempting to:
     * <ol>
     *   <li>Restore the context from the persister</li>
     *   <li>Fallback to the DB-stored state when no persisted context exists</li>
     * </ol>
     * Then seeds the extended state with the process type, current variables and incoming payload.
     *
     * @param instance process instance (for state and variables)
     * @param data     incoming payload to seed into the extended variables (may be {@code null})
     * @return a started {@link StateMachine} positioned at the correct state
     */
    private StateMachine<ProcessState, ProcessEvent> initStateMachine(ProcessInstance instance, Map<String, Object> data) {
        final String smId = instance.getId();
        final ProcessState initialState = instance.getState();

        StateMachine<ProcessState, ProcessEvent> sm = stateMachineFactory.getStateMachine(smId);
        sm.stop();

        // 1) try restore from persister
        try {
            StateMachineContext<ProcessState, ProcessEvent> persisted = stateMachinePersist.read(smId);
            if (persisted != null) {
                sm.getStateMachineAccessor().doWithAllRegions(acc ->
                        acc.resetStateMachine(persisted)
                );
            } else {
                // 2) otherwise reset from DB state
                sm.getStateMachineAccessor().doWithAllRegions(acc ->
                        acc.resetStateMachine(new DefaultStateMachineContext<ProcessState, ProcessEvent>(
                                initialState, null, null, null
                        ))
                );
            }
        } catch (Exception e) {
            // last resort fallback — hard reset to DB state
            sm.getStateMachineAccessor().doWithAllRegions(acc ->
                    acc.resetStateMachine(new DefaultStateMachineContext<ProcessState, ProcessEvent>(
                            initialState, null, null, null
                    ))
            );
        }

        // 3) extended variables — always populate with up-to-date values + incoming payload
        var ext = sm.getExtendedState().getVariables();
        ext.put(StateMachineConfig.EXT_TYPE, instance.getType());
        if (instance.getVariables() != null) ext.putAll(instance.getVariables());
        if (data != null) ext.putAll(data);

        sm.start();
        return sm;
    }

    /**
     * Persists a history record with an immutable snapshot of variables after the transition.
     * Failures are logged and ignored to keep the main flow resilient.
     *
     * @param processId process identifier
     * @param from      previous state (may be {@code null} for the initial snapshot)
     * @param to        new state
     * @param event     triggering event (may be {@code null} for the initial snapshot)
     * @param payload   incoming payload used for the transition (may be {@code null})
     */
    private void saveHistory(String processId,
                             ProcessState from,
                             ProcessState to,
                             ProcessEvent event,
                             Map<String, Object> payload) {
        try {
            // variables snapshot AFTER the transition
            Map<String, Object> snapshot = repository.findById(processId)
                    .map(ProcessInstance::getVariables)
                    .map(HashMap::new)
                    .orElseGet(HashMap::new);

            ProcessHistory h = ProcessHistory.builder()
                    .processId(processId)
                    .fromState(from)
                    .toState(to)
                    .event(event)
                    .timestamp(Instant.now())
                    .payload(payload == null ? Map.of() : new HashMap<>(payload))
                    .variablesSnapshot(snapshot)
                    .build();
            historyRepository.save(h);
        } catch (Exception e) {
            log.warn("Failed to save history for processId={}, from={}, to={}, event={}",
                    processId, from, to, event, e);
        }
    }

    /**
     * Creates a defensive mutable copy of the provided map.
     *
     * @param m source map (may be {@code null})
     * @return mutable copy (never {@code null})
     */
    @NotNull
    private static Map<String, Object> safeMap(Map<String, Object> m) {
        return (m == null) ? new HashMap<>() : new HashMap<>(m);
    }

    /**
     * Gets or creates a state machine for the given process instance.
     *
     * @param instance process instance to get state machine for
     * @return configured and started state machine
     */
    private StateMachine<ProcessState, ProcessEvent> getStateMachine(ProcessInstance instance) {
        return initStateMachine(instance, null);
    }
}
