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
        // === MINOR ACCOUNT OPENING FLOW ===
        map(ProcessType.MINOR, ProcessState.STARTED, ProcessEvent.START_FLOW);
        map(ProcessType.MINOR, ProcessState.MINOR_OCCUPATION_SCREEN, ProcessEvent.SUBMIT_OCCUPATION);
        map(ProcessType.MINOR, ProcessState.INCOME_SCREEN, ProcessEvent.SUBMIT_INCOME);
        map(ProcessType.MINOR, ProcessState.EXPENSES_SCREEN, ProcessEvent.SUBMIT_EXPENSES);
        map(ProcessType.MINOR, ProcessState.GENERATE_SCAN, ProcessEvent.GENERATE_DOCUMENT_SCAN);
        map(ProcessType.MINOR, ProcessState.SPEECH_TO_TEXT, ProcessEvent.PROCESS_SPEECH_TO_TEXT);
        map(ProcessType.MINOR, ProcessState.PERFORM_MATCH, ProcessEvent.PERFORM_DOCUMENT_MATCH);
        map(ProcessType.MINOR, ProcessState.FACE_RECOGNITION_UPLOAD, ProcessEvent.UPLOAD_FACE_RECOGNITION);
        map(ProcessType.MINOR, ProcessState.CUSTOMER_INFO_VALIDATION, ProcessEvent.VALIDATE_CUSTOMER_INFO);
        map(ProcessType.MINOR, ProcessState.SIGNATURE_EXAMPLE_SCREEN, ProcessEvent.SUBMIT_SIGNATURE);
        map(ProcessType.MINOR, ProcessState.ACCOUNT_ACTIVITIES_SCREEN, ProcessEvent.SUBMIT_ACCOUNT_ACTIVITIES);
        map(ProcessType.MINOR, ProcessState.STUDENT_PACKAGES_SCREEN, ProcessEvent.SUBMIT_STUDENT_PACKAGES);
        map(ProcessType.MINOR, ProcessState.VIDEO_SCREEN, ProcessEvent.SUBMIT_VIDEO);
        map(ProcessType.MINOR, ProcessState.CUSTOMER_ADDRESS_SCREEN, ProcessEvent.SUBMIT_ADDRESS);
        map(ProcessType.MINOR, ProcessState.CHOOSE_BRANCH_SCREEN, ProcessEvent.SUBMIT_BRANCH_CHOICE);
        map(ProcessType.MINOR, ProcessState.INFORMATION_ACTIVITIES_SCREEN, ProcessEvent.SUBMIT_INFORMATION_ACTIVITIES);
        map(ProcessType.MINOR, ProcessState.TWO_MORE_QUESTIONS_SCREEN, ProcessEvent.SUBMIT_ADDITIONAL_QUESTIONS);
        map(ProcessType.MINOR, ProcessState.SERVICE_SUBSCRIPTION, ProcessEvent.SUBSCRIBE_TO_SERVICE);
        map(ProcessType.MINOR, ProcessState.NO_SERVICE_SUBSCRIPTION, ProcessEvent.DECLINE_SERVICE);
        map(ProcessType.MINOR, ProcessState.FORMS, ProcessEvent.SUBMIT_FORMS);
        map(ProcessType.MINOR, ProcessState.WARNINGS, ProcessEvent.ACKNOWLEDGE_WARNINGS);
        map(ProcessType.MINOR, ProcessState.WELCOME, ProcessEvent.COMPLETE_WELCOME);
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
