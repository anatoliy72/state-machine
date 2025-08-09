package com.example.state_machine.service.advance;

import com.example.state_machine.model.*;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

@Component
public class StepPlan {

    private final Map<StepKey, ProcessEvent> nextEvent = Map.ofEntries(
            // SINGLE_OWNER
            entry(new StepKey(ProcessType.SINGLE_OWNER, ProcessState.STARTED), ProcessEvent.START_FLOW),
            entry(new StepKey(ProcessType.SINGLE_OWNER, ProcessState.KYC_IN_PROGRESS), ProcessEvent.KYC_VERIFIED),
            entry(new StepKey(ProcessType.SINGLE_OWNER, ProcessState.WAITING_FOR_BIOMETRY), ProcessEvent.BIOMETRY_SUCCESS),
            entry(new StepKey(ProcessType.SINGLE_OWNER, ProcessState.BIOMETRY_VERIFIED), ProcessEvent.CREATE_ACCOUNT),

            // MULTI_OWNER
            entry(new StepKey(ProcessType.MULTI_OWNER, ProcessState.STARTED), ProcessEvent.START_FLOW),
            entry(new StepKey(ProcessType.MULTI_OWNER, ProcessState.FILL_PERSONAL_DETAILS), ProcessEvent.SUBMIT_PERSONAL),
            entry(new StepKey(ProcessType.MULTI_OWNER, ProcessState.ANSWER_ACCOUNT_QUESTIONS), ProcessEvent.SUBMIT_ANSWERS),
            entry(new StepKey(ProcessType.MULTI_OWNER, ProcessState.KYC_IN_PROGRESS), ProcessEvent.KYC_VERIFIED),
            entry(new StepKey(ProcessType.MULTI_OWNER, ProcessState.WAITING_FOR_BIOMETRY), ProcessEvent.BIOMETRY_SUCCESS),
            entry(new StepKey(ProcessType.MULTI_OWNER, ProcessState.BIOMETRY_VERIFIED), ProcessEvent.ADD_OWNER),
            entry(new StepKey(ProcessType.MULTI_OWNER, ProcessState.WAITING_FOR_ALL_OWNERS), ProcessEvent.CONFIRM_ALL_OWNERS),

            // MINOR
            entry(new StepKey(ProcessType.MINOR, ProcessState.STARTED), ProcessEvent.START_FLOW),
            entry(new StepKey(ProcessType.MINOR, ProcessState.FILL_PERSONAL_DETAILS), ProcessEvent.SUBMIT_PERSONAL),
            entry(new StepKey(ProcessType.MINOR, ProcessState.ANSWER_ACCOUNT_QUESTIONS), ProcessEvent.SUBMIT_ANSWERS),
            entry(new StepKey(ProcessType.MINOR, ProcessState.KYC_IN_PROGRESS), ProcessEvent.KYC_VERIFIED),
            entry(new StepKey(ProcessType.MINOR, ProcessState.WAITING_FOR_BIOMETRY), ProcessEvent.BIOMETRY_SUCCESS),
            entry(new StepKey(ProcessType.MINOR, ProcessState.BIOMETRY_VERIFIED), ProcessEvent.REQUEST_PARENT_CONSENT),
            entry(new StepKey(ProcessType.MINOR, ProcessState.WAITING_FOR_PARENT_CONSENT), ProcessEvent.PARENT_APPROVED),
            entry(new StepKey(ProcessType.MINOR, ProcessState.ACCOUNT_CREATED_LIMITED), ProcessEvent.CREATE_ACCOUNT),

            // MINOR_TO_REGULAR
            entry(new StepKey(ProcessType.MINOR_TO_REGULAR, ProcessState.MINOR_ACCOUNT_IDENTIFIED), ProcessEvent.CONFIRM_CONVERSION),
            entry(new StepKey(ProcessType.MINOR_TO_REGULAR, ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION), ProcessEvent.COMPLETE_CONVERSION)
    );

    public Optional<ProcessEvent> next(ProcessType type, ProcessState state) {
        return Optional.ofNullable(nextEvent.get(new StepKey(type, state)));
    }
}
