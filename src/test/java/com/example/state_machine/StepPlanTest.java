package com.example.state_machine;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import com.example.state_machine.service.advance.StepPlan;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StepPlanTest {

    private final StepPlan plan = new StepPlan();

    @Test
    void singleOwner_flow_is_mapped() {
        assertEquals(ProcessEvent.START_FLOW,
                plan.next(ProcessType.SINGLE_OWNER, ProcessState.STARTED).orElseThrow());

        assertEquals(ProcessEvent.KYC_VERIFIED,
                plan.next(ProcessType.SINGLE_OWNER, ProcessState.KYC_IN_PROGRESS).orElseThrow());

        assertEquals(ProcessEvent.BIOMETRY_SUCCESS,
                plan.next(ProcessType.SINGLE_OWNER, ProcessState.WAITING_FOR_BIOMETRY).orElseThrow());

        assertEquals(ProcessEvent.CREATE_ACCOUNT,
                plan.next(ProcessType.SINGLE_OWNER, ProcessState.BIOMETRY_VERIFIED).orElseThrow());
    }

    @Test
    void multiOwner_flow_is_mapped() {
        assertEquals(ProcessEvent.START_FLOW,
                plan.next(ProcessType.MULTI_OWNER, ProcessState.STARTED).orElseThrow());
        assertEquals(ProcessEvent.SUBMIT_PERSONAL,
                plan.next(ProcessType.MULTI_OWNER, ProcessState.FILL_PERSONAL_DETAILS).orElseThrow());
        assertEquals(ProcessEvent.SUBMIT_ANSWERS,
                plan.next(ProcessType.MULTI_OWNER, ProcessState.ANSWER_ACCOUNT_QUESTIONS).orElseThrow());
        assertEquals(ProcessEvent.KYC_VERIFIED,
                plan.next(ProcessType.MULTI_OWNER, ProcessState.KYC_IN_PROGRESS).orElseThrow());
        assertEquals(ProcessEvent.BIOMETRY_SUCCESS,
                plan.next(ProcessType.MULTI_OWNER, ProcessState.WAITING_FOR_BIOMETRY).orElseThrow());
        assertEquals(ProcessEvent.ADD_OWNER,
                plan.next(ProcessType.MULTI_OWNER, ProcessState.BIOMETRY_VERIFIED).orElseThrow());
        assertEquals(ProcessEvent.CONFIRM_ALL_OWNERS,
                plan.next(ProcessType.MULTI_OWNER, ProcessState.WAITING_FOR_ALL_OWNERS).orElseThrow());
    }

    @Test
    void minor_flow_is_mapped() {
        assertEquals(ProcessEvent.START_FLOW,
                plan.next(ProcessType.MINOR, ProcessState.STARTED).orElseThrow());
        assertEquals(ProcessEvent.SUBMIT_PERSONAL,
                plan.next(ProcessType.MINOR, ProcessState.FILL_PERSONAL_DETAILS).orElseThrow());
        assertEquals(ProcessEvent.SUBMIT_ANSWERS,
                plan.next(ProcessType.MINOR, ProcessState.ANSWER_ACCOUNT_QUESTIONS).orElseThrow());
        assertEquals(ProcessEvent.KYC_VERIFIED,
                plan.next(ProcessType.MINOR, ProcessState.KYC_IN_PROGRESS).orElseThrow());
        assertEquals(ProcessEvent.BIOMETRY_SUCCESS,
                plan.next(ProcessType.MINOR, ProcessState.WAITING_FOR_BIOMETRY).orElseThrow());
        assertEquals(ProcessEvent.REQUEST_PARENT_CONSENT,
                plan.next(ProcessType.MINOR, ProcessState.BIOMETRY_VERIFIED).orElseThrow());
        assertEquals(ProcessEvent.PARENT_APPROVED,
                plan.next(ProcessType.MINOR, ProcessState.WAITING_FOR_PARENT_CONSENT).orElseThrow());
    }

    @Test
    void minor_to_regular_flow_is_mapped() {
        assertEquals(ProcessEvent.CONFIRM_CONVERSION,
                plan.next(ProcessType.MINOR_TO_REGULAR, ProcessState.MINOR_ACCOUNT_IDENTIFIED).orElseThrow());
        assertEquals(ProcessEvent.COMPLETE_CONVERSION,
                plan.next(ProcessType.MINOR_TO_REGULAR, ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION).orElseThrow());
    }

    @Test
    void unknown_state_returns_empty() {
        Optional<ProcessEvent> next = plan.next(ProcessType.SINGLE_OWNER, ProcessState.ACCOUNT_CREATED);
        assertTrue(next.isEmpty());
    }
}
