package com.example.state_machine.config;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = { StateMachineConfig.class })
class StateMachineConfigTest {

    @Autowired
    private StateMachineFactory<ProcessState, ProcessEvent> factory;

    private StateMachine<ProcessState, ProcessEvent> sm;
    private ProcessType currentType;

    @BeforeEach
    void setUp() {
        sm = factory.getStateMachine();
        sm.start();
        currentType = null;
    }

    /** Ensure the guard sees the right process type. */
    private void setType(ProcessType type) {
        this.currentType = type;
        sm.getExtendedState().getVariables()
                .put(StateMachineConfig.EXT_TYPE, type);
    }

    /** Reset machine to a state and reapply current type for guards. */
    private void resetTo(ProcessState state) {
        sm.stop();
        sm.getStateMachineAccessor().doWithAllRegions(access ->
                access.resetStateMachine(new DefaultStateMachineContext<>(state, null, null, null))
        );
        sm.start();
        if (currentType != null) {
            sm.getExtendedState().getVariables()
                    .put(StateMachineConfig.EXT_TYPE, currentType);
        }
        assertEquals(state, sm.getState().getId(), "Failed to reset to expected state");
    }

    private void send(ProcessEvent event, ProcessState expected) {
        boolean accepted = sm.sendEvent(event);
        assertTrue(accepted, "Event was not accepted: " + event);
        assertEquals(expected, sm.getState().getId(),
                "Unexpected state after event: " + event);
    }

    @Test
    @DisplayName("Single Owner: KYC -> Biometry -> Create Account")
    void singleOwnerFlow() {
        setType(ProcessType.SINGLE_OWNER);
        resetTo(ProcessState.KYC_IN_PROGRESS);
        send(ProcessEvent.KYC_VERIFIED, ProcessState.WAITING_FOR_BIOMETRY);
        send(ProcessEvent.BIOMETRY_SUCCESS, ProcessState.BIOMETRY_VERIFIED);
        send(ProcessEvent.CREATE_ACCOUNT, ProcessState.ACCOUNT_CREATED);
    }

    @Test
    @DisplayName("Multi Owner: Full happy path to ACCOUNT_CREATED")
    void multiOwnerFlow() {
        setType(ProcessType.MULTI_OWNER);
        assertEquals(ProcessState.STARTED, sm.getState().getId());

        send(ProcessEvent.START_FLOW, ProcessState.FILL_PERSONAL_DETAILS);
        send(ProcessEvent.SUBMIT_PERSONAL, ProcessState.ANSWER_ACCOUNT_QUESTIONS);
        send(ProcessEvent.SUBMIT_ANSWERS, ProcessState.KYC_IN_PROGRESS);
        send(ProcessEvent.KYC_VERIFIED, ProcessState.WAITING_FOR_BIOMETRY);
        send(ProcessEvent.BIOMETRY_SUCCESS, ProcessState.BIOMETRY_VERIFIED);
        send(ProcessEvent.ADD_OWNER, ProcessState.WAITING_FOR_ALL_OWNERS);
        send(ProcessEvent.CONFIRM_ALL_OWNERS, ProcessState.ACCOUNT_CREATED);
    }

    @Test
    @DisplayName("Minor: Biometry -> Parent consent -> Limited -> Identified")
    void minorFlow() {
        setType(ProcessType.MINOR);
        assertEquals(ProcessState.STARTED, sm.getState().getId());

        send(ProcessEvent.START_FLOW, ProcessState.FILL_PERSONAL_DETAILS);
        send(ProcessEvent.SUBMIT_PERSONAL, ProcessState.ANSWER_ACCOUNT_QUESTIONS);
        send(ProcessEvent.SUBMIT_ANSWERS, ProcessState.KYC_IN_PROGRESS);
        send(ProcessEvent.KYC_VERIFIED, ProcessState.WAITING_FOR_BIOMETRY);
        send(ProcessEvent.BIOMETRY_SUCCESS, ProcessState.BIOMETRY_VERIFIED);
        send(ProcessEvent.REQUEST_PARENT_CONSENT, ProcessState.WAITING_FOR_PARENT_CONSENT);
        send(ProcessEvent.PARENT_APPROVED, ProcessState.ACCOUNT_CREATED_LIMITED);
        send(ProcessEvent.CREATE_ACCOUNT, ProcessState.MINOR_ACCOUNT_IDENTIFIED);
    }

    @Test
    @DisplayName("Minor -> Regular conversion")
    void minorToRegularConversion() {
        setType(ProcessType.MINOR_TO_REGULAR);
        resetTo(ProcessState.MINOR_ACCOUNT_IDENTIFIED);
        send(ProcessEvent.CONFIRM_CONVERSION, ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION);
        send(ProcessEvent.COMPLETE_CONVERSION, ProcessState.ACCOUNT_CONVERTED_TO_REGULAR);
    }

    @Test
    @DisplayName("Negative: unexpected event is ignored at STARTED")
    void unexpectedEventIgnored() {
        // Use any type; event should still be rejected from STARTED.
        setType(ProcessType.SINGLE_OWNER);
        assertEquals(ProcessState.STARTED, sm.getState().getId());
        boolean accepted = sm.sendEvent(ProcessEvent.CREATE_ACCOUNT);
        assertFalse(accepted, "Event should not be accepted at STARTED");
        assertEquals(ProcessState.STARTED, sm.getState().getId(), "State must remain STARTED");
    }
}
