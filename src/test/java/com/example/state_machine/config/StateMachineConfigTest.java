package com.example.state_machine.config;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;

import java.util.Map;

import static com.example.state_machine.config.StateMachineConfig.EXT_TYPE;
import static com.example.state_machine.model.ProcessState.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StateMachineConfigTest {

    @Autowired
    private StateMachineFactory<ProcessState, ProcessEvent> factory;

    private StateMachine<ProcessState, ProcessEvent> stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = factory.getStateMachine();
        stateMachine.start();
        stateMachine.getExtendedState().getVariables().put(EXT_TYPE, ProcessType.MINOR);
    }

    @Test
    void whenSendMinorOccupationEvent_thenMoveToOccupationScreen() {
        boolean result = stateMachine.sendEvent(ProcessEvent.MINOR_OCCUPATION_SCREEN);
        assertTrue(result);
        assertEquals(MINOR_OCCUPATION_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void whenSendIncomeEvent_thenMoveToIncomeScreen() {
        // Move to occupation first
        assertTrue(stateMachine.sendEvent(ProcessEvent.MINOR_OCCUPATION_SCREEN));

        // Then move to income
        boolean result = stateMachine.sendEvent(ProcessEvent.INCOME_SCREEN);
        assertTrue(result);
        assertEquals(INCOME_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void whenAtIncome_thenSendMinorOccupationEvent_movesBackToOccupation() {
        // Start flow: STARTED -> MINOR_OCCUPATION_SCREEN -> INCOME_SCREEN
        assertTrue(stateMachine.sendEvent(ProcessEvent.MINOR_OCCUPATION_SCREEN));
        assertTrue(stateMachine.sendEvent(ProcessEvent.INCOME_SCREEN));

        // Go back
        boolean result = stateMachine.sendEvent(ProcessEvent.MINOR_OCCUPATION_SCREEN);
        assertTrue(result);
        assertEquals(MINOR_OCCUPATION_SCREEN, stateMachine.getState().getId());
    }

    private void setupStateAndVariables(ProcessState state, Map<String, Object> variables) {
        stateMachine = factory.getStateMachine();
        stateMachine.start();
        try {
            stateMachine.getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachine(
                    new DefaultStateMachineContext<>(state, null, null, null)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set state", e);
        }
        if (variables != null) {
            variables.forEach((k, v) -> stateMachine.getExtendedState().getVariables().put(k, v));
        }
    }
}
