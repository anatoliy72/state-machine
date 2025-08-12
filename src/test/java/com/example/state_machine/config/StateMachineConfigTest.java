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
import org.springframework.statemachine.support.DefaultExtendedState;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "workflow.strict-guards=true",
        "workflow.voice-score-threshold=0.95"
})
class StateMachineConfigTest {

    @Autowired
    private StateMachineFactory<ProcessState, ProcessEvent> factory;

    private StateMachine<ProcessState, ProcessEvent> sm;
    private ProcessType currentType;

    @BeforeEach
    void setUp() {
        sm = factory.getStateMachine();
        sm.stop();
        // Заполняем extended vars: тип процесса обязателен для guard'ов
        var ext = new DefaultExtendedState();
        ext.getVariables().put(StateMachineConfig.EXT_TYPE, ProcessType.SINGLE_OWNER);
        sm.getExtendedState().getVariables().putAll(ext.getVariables());
        sm.start();
    }

    // ---------- helpers ----------

    private boolean send(ProcessEvent e) {
        return sm.sendEvent(e);
    }

    private boolean send(ProcessEvent e, Map<String, Object> payload) {
        if (payload != null) {
            sm.getExtendedState().getVariables().putAll(payload);
        }
        return sm.sendEvent(e);
    }

    private void setType(ProcessType type) {
        sm.getExtendedState().getVariables().put(StateMachineConfig.EXT_TYPE, type);
    }

    // ---------- SINGLE_OWNER: Non-US ветка ----------

    @Test
    void singleOwner_nonUs_happyPath() {
        setType(ProcessType.SINGLE_OWNER);

        // STARTED -> ANSWER_ACCOUNT_QUESTIONS
        assertTrue(send(ProcessEvent.START_FLOW), "START_FLOW not accepted");
        assertEquals(ProcessState.ANSWER_ACCOUNT_QUESTIONS, sm.getState().getId());

        // ANSWER_ACCOUNT_QUESTIONS -> KYC_IN_PROGRESS (usCitizen=false)
        assertTrue(send(ProcessEvent.SUBMIT_ANSWERS, Map.of("usCitizen", false)));
        assertEquals(ProcessState.KYC_IN_PROGRESS, sm.getState().getId());

        // KYC_IN_PROGRESS -> WAITING_FOR_BIOMETRY (status APPROVED)
        assertTrue(send(ProcessEvent.KYC_VERIFIED, Map.of("status", "APPROVED")));
        assertEquals(ProcessState.WAITING_FOR_BIOMETRY, sm.getState().getId());

        // WAITING_FOR_BIOMETRY -> BIOMETRY_VERIFIED (matchScore >= 0.90)
        assertTrue(send(ProcessEvent.BIOMETRY_SUCCESS, Map.of("matchScore", 0.96)));
        assertEquals(ProcessState.BIOMETRY_VERIFIED, sm.getState().getId());

        // BIOMETRY_VERIFIED -> ACCOUNT_CREATED (voiceScore > 0.95)
        assertTrue(send(ProcessEvent.CREATE_ACCOUNT, Map.of("voiceScore", 0.97)));
        assertEquals(ProcessState.ACCOUNT_CREATED, sm.getState().getId());
    }

    // ---------- SINGLE_OWNER: US-ветка с промежуточным шагом US_PASSPORT_DETAILS ----------

    @Test
    void singleOwner_us_branch_happyPath() {
        setType(ProcessType.SINGLE_OWNER);

        // STARTED -> ANSWER_ACCOUNT_QUESTIONS
        assertTrue(send(ProcessEvent.START_FLOW));
        assertEquals(ProcessState.ANSWER_ACCOUNT_QUESTIONS, sm.getState().getId());

        // ANSWER_ACCOUNT_QUESTIONS -> US_PASSPORT_DETAILS (usCitizen = true)
        assertTrue(send(ProcessEvent.SUBMIT_ANSWERS, Map.of("usCitizen", true)));
        assertEquals(ProcessState.US_PASSPORT_DETAILS, sm.getState().getId(), "Must branch to US_PASSPORT_DETAILS");

        // US_PASSPORT_DETAILS -> KYC_IN_PROGRESS
        assertTrue(send(ProcessEvent.SUBMIT_US_PASSPORT, Map.of("usPassportNo", "123456789", "ssn", "123-45-6789")));
        assertEquals(ProcessState.KYC_IN_PROGRESS, sm.getState().getId());

        // KYC_IN_PROGRESS -> WAITING_FOR_BIOMETRY
        assertTrue(send(ProcessEvent.KYC_VERIFIED, Map.of("status", "APPROVED")));
        assertEquals(ProcessState.WAITING_FOR_BIOMETRY, sm.getState().getId());

        // WAITING_FOR_BIOMETRY -> BIOMETRY_VERIFIED
        assertTrue(send(ProcessEvent.BIOMETRY_SUCCESS, Map.of("matchScore", 0.95)));
        assertEquals(ProcessState.BIOMETRY_VERIFIED, sm.getState().getId());

        // BIOMETRY_VERIFIED -> ACCOUNT_CREATED (voiceScore strictly > 0.95)
        assertTrue(send(ProcessEvent.CREATE_ACCOUNT, Map.of("voiceScore", 0.98)));
        assertEquals(ProcessState.ACCOUNT_CREATED, sm.getState().getId());
    }

    // ---------- BACK запрещён из KYC_IN_PROGRESS для SINGLE_OWNER ----------

    @Test
    void singleOwner_backFromKycInProgress_isRejected() {
        setType(ProcessType.SINGLE_OWNER);

        assertTrue(send(ProcessEvent.START_FLOW));
        assertEquals(ProcessState.ANSWER_ACCOUNT_QUESTIONS, sm.getState().getId());

        assertTrue(send(ProcessEvent.SUBMIT_ANSWERS, Map.of("usCitizen", false)));
        assertEquals(ProcessState.KYC_IN_PROGRESS, sm.getState().getId());

        // BACK из KYC_IN_PROGRESS — запрещён
        assertFalse(send(ProcessEvent.BACK), "BACK from KYC_IN_PROGRESS must be rejected");
        assertEquals(ProcessState.KYC_IN_PROGRESS, sm.getState().getId());
    }

    // ---------- MULTI_OWNER: BACK на ранних шагах разрешён, из KYC_IN_PROGRESS — запрещён ----------

    @Test
    void multiOwner_back_allowed_onEarlySteps_butRejectedFromKycInProgress() {
        setType(ProcessType.MULTI_OWNER);

        // STARTED -> FILL_PERSONAL_DETAILS
        assertTrue(send(ProcessEvent.START_FLOW));
        assertEquals(ProcessState.FILL_PERSONAL_DETAILS, sm.getState().getId());

        // BACK -> STARTED (разрешён)
        assertTrue(send(ProcessEvent.BACK));
        assertEquals(ProcessState.STARTED, sm.getState().getId());

        // Снова вперёд до ANSWER_ACCOUNT_QUESTIONS
        assertTrue(send(ProcessEvent.START_FLOW));
        assertEquals(ProcessState.FILL_PERSONAL_DETAILS, sm.getState().getId());
        assertTrue(send(ProcessEvent.SUBMIT_PERSONAL));
        assertEquals(ProcessState.ANSWER_ACCOUNT_QUESTIONS, sm.getState().getId());

        // BACK -> FILL_PERSONAL_DETAILS (разрешён)
        assertTrue(send(ProcessEvent.BACK));
        assertEquals(ProcessState.FILL_PERSONAL_DETAILS, sm.getState().getId());

        // Вперёд до KYC_IN_PROGRESS
        assertTrue(send(ProcessEvent.SUBMIT_PERSONAL));
        assertEquals(ProcessState.ANSWER_ACCOUNT_QUESTIONS, sm.getState().getId());
        assertTrue(send(ProcessEvent.SUBMIT_ANSWERS));
        assertEquals(ProcessState.KYC_IN_PROGRESS, sm.getState().getId());

        // BACK из KYC_IN_PROGRESS — запрещён для MULTI_OWNER
        assertFalse(send(ProcessEvent.BACK), "BACK from KYC_IN_PROGRESS must be rejected for MULTI_OWNER");
        assertEquals(ProcessState.KYC_IN_PROGRESS, sm.getState().getId());
    }

}
