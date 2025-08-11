package com.example.state_machine;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import com.example.state_machine.service.advance.preconditions.VoiceScoreSatisfiedPrecondition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VoiceScoreSatisfiedPreconditionTest {

    private final VoiceScoreSatisfiedPrecondition pre = new VoiceScoreSatisfiedPrecondition();

    private ProcessInstance instanceWithVars(Map<String, Object> vars) {
        return ProcessInstance.builder()
                .id("p1")
                .clientId("c1")
                .type(ProcessType.SINGLE_OWNER)
                .state(ProcessState.BIOMETRY_VERIFIED)
                .variables(vars != null ? new HashMap<>(vars) : new HashMap<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void supports_only_final_events() {
        assertTrue(pre.supports(ProcessType.SINGLE_OWNER, ProcessState.BIOMETRY_VERIFIED, ProcessEvent.CREATE_ACCOUNT));
        assertFalse(pre.supports(ProcessType.SINGLE_OWNER, ProcessState.STARTED, ProcessEvent.START_FLOW));
        assertTrue(pre.supports(ProcessType.MULTI_OWNER, ProcessState.WAITING_FOR_ALL_OWNERS, ProcessEvent.CONFIRM_ALL_OWNERS));
        assertTrue(pre.supports(ProcessType.MINOR, ProcessState.WAITING_FOR_PARENT_CONSENT, ProcessEvent.PARENT_APPROVED));
        assertTrue(pre.supports(ProcessType.MINOR_TO_REGULAR, ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION, ProcessEvent.COMPLETE_CONVERSION));
    }

    @Test
    void passes_when_payload_has_voiceScore_gt_threshold() {
        var pi = instanceWithVars(Map.of());
        var errs = pre.validate(pi, Map.of("voiceScore", 0.951));
        assertTrue(errs.isEmpty(), "Expected no errors when payload voiceScore > 0.95");
    }

    @Test
    void passes_when_variables_have_voiceScore_gt_threshold() {
        var pi = instanceWithVars(Map.of("voiceScore", 0.99));
        var errs = pre.validate(pi, Map.of());
        assertTrue(errs.isEmpty(), "Expected no errors when variables voiceScore > 0.95");
    }

    @Test
    void fails_when_no_voiceScore_anywhere() {
        var pi = instanceWithVars(Map.of());
        List<?> errs = pre.validate(pi, Map.of());
        assertFalse(errs.isEmpty(), "Expected error when voiceScore is missing");
    }

    @Test
    void fails_when_voiceScore_not_enough() {
        var pi = instanceWithVars(Map.of("voiceScore", 0.95)); // threshold is strictly greater
        var errs = pre.validate(pi, Map.of());
        assertFalse(errs.isEmpty(), "Expected error when voiceScore <= 0.95");
    }

    @Test
    void accepts_numeric_strings() {
        var pi = instanceWithVars(Map.of("voiceScore", "0.960"));
        var errs = pre.validate(pi, Map.of());
        assertTrue(errs.isEmpty(), "String number > 0.95 should pass");
    }
}
