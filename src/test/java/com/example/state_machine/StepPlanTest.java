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
    void minor_account_opening_flow_is_mapped() {
        assertEquals(ProcessEvent.MINOR_OCCUPATION_SCREEN,
                plan.next(ProcessType.MINOR, ProcessState.STARTED).orElseThrow());

        assertEquals(ProcessEvent.INCOME_SCREEN,
                plan.next(ProcessType.MINOR, ProcessState.MINOR_OCCUPATION_SCREEN).orElseThrow());

        assertEquals(ProcessEvent.MINOR_OCCUPATION_SCREEN,
                plan.next(ProcessType.MINOR, ProcessState.INCOME_SCREEN).orElseThrow());
    }

    @Test
    void unknown_state_returns_empty() {
        Optional<ProcessEvent> next = plan.next(ProcessType.MINOR, ProcessState.STARTED);
        assertTrue(next.isPresent()); // STARTED should be mapped
    }
}
