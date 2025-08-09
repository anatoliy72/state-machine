package com.example.state_machine.service;

import com.example.state_machine.config.StateMachineConfig;
import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import com.example.state_machine.repository.ProcessInstanceRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowService {
    private final ProcessInstanceRepository repository;
    private final StateMachineFactory<ProcessState, ProcessEvent> stateMachineFactory;
    private final StateMachinePersist<ProcessState, ProcessEvent, String> stateMachinePersist;

    @Transactional
    public ProcessInstance startProcess(String clientId, ProcessType type, Map<String, Object> initialData) {
        Assert.hasText(clientId, "clientId must not be blank");
        Assert.notNull(type, "type must not be null");

        // IMPORTANT: mutable map instead of Map.of()
        Map<String, Object> vars = (initialData != null) ? new HashMap<>(initialData) : new HashMap<>();

        ProcessInstance instance = ProcessInstance.builder()
                .clientId(clientId)
                .type(type)
                .state(ProcessState.STARTED)
                .variables(vars)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        repository.save(instance);
        return instance;
    }

    @Transactional
    public ProcessInstance handleEvent(String processId, ProcessEvent event, Map<String, Object> data) {
        ProcessInstance instance = repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));

        StateMachine<ProcessState, ProcessEvent> sm = stateMachineFactory.getStateMachine(processId);

        // 1) Stop and reset the state machine to the process state stored in DB
        sm.stop();
        sm.getStateMachineAccessor().doWithAllRegions(access ->
                access.resetStateMachine(new DefaultStateMachineContext<>(
                        instance.getState(), null, null, null
                ))
        );

        // 2) Put process type and variables into ExtendedState BEFORE start() and BEFORE sendEvent()
        Map<Object, Object> ext = sm.getExtendedState().getVariables();
        ext.put(StateMachineConfig.EXT_TYPE, instance.getType()); // critical for guard checks

        if (instance.getVariables() != null) {
            ext.putAll(instance.getVariables());
        }
        if (data != null) {
            ext.putAll(data);
        }

        sm.start();

        boolean accepted = sm.sendEvent(event);
        if (!accepted) {
            log.warn("Event not accepted. id={}, state={}, event={}", processId, instance.getState(), event);
            throw new IllegalStateException("Event not accepted: " + event);
        }

        // 3) Update instance state and variables
        instance.setState(sm.getState().getId());
        return getProcessInstance(data, instance);
    }

    @NotNull
    private ProcessInstance getProcessInstance(Map<String, Object> data, ProcessInstance instance) {
        if (data != null && !data.isEmpty()) {
            // ensure mutability
            if (instance.getVariables() == null || !(instance.getVariables() instanceof HashMap)) {
                instance.setVariables(new HashMap<>(instance.getVariables() == null ? Map.of() : instance.getVariables()));
            }
            instance.getVariables().putAll(data);
        }
        instance.setUpdatedAt(Instant.now());
        repository.save(instance);

        return instance;
    }

    @Transactional
    public ProcessInstance updateVariables(String processId, Map<String, Object> updates) {
        ProcessInstance instance = repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));

        return getProcessInstance(updates, instance);
    }

    @Transactional(readOnly = true)
    public ProcessInstance getProcess(String processId) {
        return repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));
    }

    @Transactional
    public ProcessInstance startMinorToRegularConversion(String clientId,
                                                         String minorAccountId,
                                                         Map<String, Object> initialData) {
        Assert.hasText(clientId, "clientId must not be blank");

        // Domain validation (simplified):
        // Here you can verify that minorAccountId corresponds to an actual MINOR account.
        // If no such validation exists yet — just log it.
        if (minorAccountId == null || minorAccountId.isBlank()) {
            log.warn("startMinorToRegularConversion without minorAccountId (audit recommended)");
        }

        // Create mutable variables map and store the linked account for audit/tracing
        Map<String, Object> vars = new java.util.HashMap<>();
        if (initialData != null) vars.putAll(initialData);
        if (minorAccountId != null) vars.put("linkedMinorAccountId", minorAccountId);

        ProcessInstance instance = ProcessInstance.builder()
                .clientId(clientId)
                .type(ProcessType.MINOR_TO_REGULAR)
                // KEY: start directly from MINOR_ACCOUNT_IDENTIFIED —
                // this is a "valid start node" for the conversion branch
                .state(ProcessState.MINOR_ACCOUNT_IDENTIFIED)
                .variables(vars)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        repository.save(instance);

        // (Optional) Warm up the state machine — synchronize it immediately
        StateMachine<ProcessState, ProcessEvent> sm = stateMachineFactory.getStateMachine(instance.getId());
        sm.stop();
        sm.getStateMachineAccessor().doWithAllRegions(acc ->
                acc.resetStateMachine(new org.springframework.statemachine.support.DefaultStateMachineContext<>(
                        instance.getState(), null, null, null
                ))
        );
        // Put process type and variables into ExtendedState so guards are ready for the next event
        sm.getExtendedState().getVariables().clear();
        sm.getExtendedState().getVariables().put(com.example.state_machine.config.StateMachineConfig.EXT_TYPE, instance.getType());
        sm.getExtendedState().getVariables().putAll(instance.getVariables());
        sm.start();

        return instance;
    }
}
