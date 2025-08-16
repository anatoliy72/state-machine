package com.example.state_machine.service.advance;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps process type and current state to the next server-driven event.
 * <p>
 * This component defines the server-driven flow progression for each process type.
 * The state machine configuration handles the actual transitions and guards.
 */
@Component
public class StepPlan {

    private final Map<ProcessType, Map<ProcessState, ProcessEvent>> plan = new EnumMap<>(ProcessType.class);

    public StepPlan() {
        // === MINOR ACCOUNT OPENING FLOW (aligned with StateMachineConfig) ===
        map(ProcessType.MINOR, ProcessState.STARTED, ProcessEvent.START_FLOW);
        map(ProcessType.MINOR, ProcessState.MINOR_OCCUPATION_SCREEN, ProcessEvent.SUBMIT_OCCUPATION);
        map(ProcessType.MINOR, ProcessState.INCOME_SCREEN, ProcessEvent.SUBMIT_INCOME);
        // From EXPENSES_SCREEN we continue via CONTINUE_FLOW (not SUBMIT_EXPENSES)
        map(ProcessType.MINOR, ProcessState.EXPENSES_SCREEN, ProcessEvent.CONTINUE_FLOW);
        map(ProcessType.MINOR, ProcessState.GENERATE_SCAN, ProcessEvent.GENERATE_DOCUMENT_SCAN);
        map(ProcessType.MINOR, ProcessState.SPEECH_TO_TEXT, ProcessEvent.PROCESS_SPEECH_TO_TEXT);
        map(ProcessType.MINOR, ProcessState.PERFORM_MATCH, ProcessEvent.PERFORM_DOCUMENT_MATCH);
        // Face recognition screen advances by submitting signature
        map(ProcessType.MINOR, ProcessState.FACE_RECOGNITION_UPLOAD, ProcessEvent.SUBMIT_SIGNATURE);
        // Account activities and student packages
        map(ProcessType.MINOR, ProcessState.ACCOUNT_ACTIVITIES_SCREEN, ProcessEvent.SUBMIT_ACCOUNT_ACTIVITIES);
        map(ProcessType.MINOR, ProcessState.STUDENT_PACKAGES_SCREEN, ProcessEvent.SUBMIT_STUDENT_PACKAGES);
        // From VIDEO we also advance via CONTINUE_FLOW
        map(ProcessType.MINOR, ProcessState.VIDEO_SCREEN, ProcessEvent.CONTINUE_FLOW);
        // Address and branch steps
        map(ProcessType.MINOR, ProcessState.CUSTOMER_ADDRESS_SCREEN, ProcessEvent.SUBMIT_ADDRESS);
        map(ProcessType.MINOR, ProcessState.CHOOSE_BRANCH_SCREEN, ProcessEvent.SUBMIT_BRANCH_CHOICE);
        map(ProcessType.MINOR, ProcessState.INFORMATION_ACTIVITIES_SCREEN, ProcessEvent.SUBMIT_INFORMATION_ACTIVITIES);
        // TWO_MORE_QUESTIONS_SCREEN: single server-driven event with branching by guards
        map(ProcessType.MINOR, ProcessState.TWO_MORE_QUESTIONS_SCREEN, ProcessEvent.SUBMIT_ADDITIONAL_QUESTIONS);
        // Service subscription steps submit forms to proceed
        map(ProcessType.MINOR, ProcessState.SERVICE_SUBSCRIPTION, ProcessEvent.SUBMIT_FORMS);
        map(ProcessType.MINOR, ProcessState.NO_SERVICE_SUBSCRIPTION, ProcessEvent.SUBMIT_FORMS);
        // Warnings and welcome
        map(ProcessType.MINOR, ProcessState.WARNINGS, ProcessEvent.ACKNOWLEDGE_WARNINGS);
        map(ProcessType.MINOR, ProcessState.WELCOME, ProcessEvent.COMPLETE_WELCOME);

        // === MINOR TO REGULAR FLOW (aligned with StateMachineConfig) ===
        map(ProcessType.MINOR_TO_REGULAR, ProcessState.STARTED, ProcessEvent.START_FLOW);
        map(ProcessType.MINOR_TO_REGULAR, ProcessState.INCOME_SCREEN, ProcessEvent.SUBMIT_INCOME);
        map(ProcessType.MINOR_TO_REGULAR, ProcessState.PERFORM_MATCH, ProcessEvent.PERFORM_DOCUMENT_MATCH);
        map(ProcessType.MINOR_TO_REGULAR, ProcessState.WELCOME, ProcessEvent.COMPLETE_WELCOME);
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
