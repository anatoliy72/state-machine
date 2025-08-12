package com.example.state_machine.service.advance;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-driven transition plan used by the {@code /advance} endpoint.
 * <p>
 * The plan maps a pair {@link ProcessType} × current {@link ProcessState}
 * to the next {@link ProcessEvent} that should be fired automatically.
 * <p>
 * Note: voice score validation is enforced by a dedicated {@code Precondition}
 * on the final steps and is not encoded in this plan.
 */
@Component
public class StepPlan {

    private final Map<ProcessType, Map<ProcessState, ProcessEvent>> plan = new EnumMap<>(ProcessType.class);

    public StepPlan() {
        // === SINGLE_OWNER ===
        map(ProcessType.SINGLE_OWNER, ProcessState.STARTED, ProcessEvent.START_FLOW);
        map(ProcessType.SINGLE_OWNER, ProcessState.ANSWER_ACCOUNT_QUESTIONS, ProcessEvent.SUBMIT_ANSWERS);
        // если usCitizen=true -> SM пойдёт в US_PASSPORT_DETAILS, иначе — сразу KYC_IN_PROGRESS
        map(ProcessType.SINGLE_OWNER, ProcessState.US_PASSPORT_DETAILS, ProcessEvent.SUBMIT_US_PASSPORT);
        map(ProcessType.SINGLE_OWNER, ProcessState.KYC_IN_PROGRESS, ProcessEvent.KYC_VERIFIED);
        map(ProcessType.SINGLE_OWNER, ProcessState.WAITING_FOR_BIOMETRY, ProcessEvent.BIOMETRY_SUCCESS);
        map(ProcessType.SINGLE_OWNER, ProcessState.BIOMETRY_VERIFIED, ProcessEvent.CREATE_ACCOUNT);

        // === MULTI_OWNER ===
        map(ProcessType.MULTI_OWNER, ProcessState.STARTED, ProcessEvent.START_FLOW);
        map(ProcessType.MULTI_OWNER, ProcessState.FILL_PERSONAL_DETAILS, ProcessEvent.SUBMIT_PERSONAL);
        map(ProcessType.MULTI_OWNER, ProcessState.ANSWER_ACCOUNT_QUESTIONS, ProcessEvent.SUBMIT_ANSWERS);
        map(ProcessType.MULTI_OWNER, ProcessState.KYC_IN_PROGRESS, ProcessEvent.KYC_VERIFIED);
        map(ProcessType.MULTI_OWNER, ProcessState.WAITING_FOR_BIOMETRY, ProcessEvent.BIOMETRY_SUCCESS);
        map(ProcessType.MULTI_OWNER, ProcessState.BIOMETRY_VERIFIED, ProcessEvent.ADD_OWNER);
        map(ProcessType.MULTI_OWNER, ProcessState.WAITING_FOR_ALL_OWNERS, ProcessEvent.CONFIRM_ALL_OWNERS);

        // === MINOR ===
        map(ProcessType.MINOR, ProcessState.STARTED, ProcessEvent.START_FLOW);
        map(ProcessType.MINOR, ProcessState.FILL_PERSONAL_DETAILS, ProcessEvent.SUBMIT_PERSONAL);
        map(ProcessType.MINOR, ProcessState.ANSWER_ACCOUNT_QUESTIONS, ProcessEvent.SUBMIT_ANSWERS);
        map(ProcessType.MINOR, ProcessState.KYC_IN_PROGRESS, ProcessEvent.KYC_VERIFIED);
        map(ProcessType.MINOR, ProcessState.WAITING_FOR_BIOMETRY, ProcessEvent.BIOMETRY_SUCCESS);
        map(ProcessType.MINOR, ProcessState.BIOMETRY_VERIFIED, ProcessEvent.REQUEST_PARENT_CONSENT);
        map(ProcessType.MINOR, ProcessState.WAITING_FOR_PARENT_CONSENT, ProcessEvent.PARENT_APPROVED);

        // === MINOR_TO_REGULAR ===
        map(ProcessType.MINOR_TO_REGULAR, ProcessState.MINOR_ACCOUNT_IDENTIFIED, ProcessEvent.CONFIRM_CONVERSION);
        map(ProcessType.MINOR_TO_REGULAR, ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION, ProcessEvent.COMPLETE_CONVERSION);
    }

    /**
     * Returns the next server-driven event for a given process type and current state.
     *
     * @param type  the process type
     * @param state the current state
     * @return an {@link Optional} containing the next {@link ProcessEvent} if found; otherwise empty
     */
    public Optional<ProcessEvent> next(ProcessType type, ProcessState state) {
        return Optional.ofNullable(plan.getOrDefault(type, Map.of()).get(state));
    }

    /**
     * Registers a mapping from the given {@code from} state to a {@code nextEvent}
     * for the specified process {@code type}.
     *
     * @param type      process type bucket
     * @param from      current state
     * @param nextEvent event that should be fired to advance from the current state
     */
    private void map(ProcessType type, ProcessState from, ProcessEvent nextEvent) {
        plan.computeIfAbsent(type, t -> new EnumMap<>(ProcessState.class)).put(from, nextEvent);
    }
}
