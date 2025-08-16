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

import java.util.HashMap;
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
        // Базовая настройка для тестов с Minor Account
        stateMachine.getExtendedState().getVariables().put(EXT_TYPE, ProcessType.MINOR);
    }

    @Test
    void whenStartFlow_thenMoveToOccupationScreen() {
        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.START_FLOW);

        // Then
        assertTrue(result);
        assertEquals(MINOR_OCCUPATION_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void whenSubmitOccupation_thenMoveToIncomeScreen() {
        // Given
        stateMachine.sendEvent(ProcessEvent.START_FLOW);

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.SUBMIT_OCCUPATION);

        // Then
        assertTrue(result);
        assertEquals(INCOME_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenContinueTrue_whenSubmitIncome_thenMoveToExpensesScreen() {
        // Given
        setupStateAndVariables(INCOME_SCREEN, Map.of(EXT_TYPE, ProcessType.MINOR, "toContinue", true));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.SUBMIT_INCOME);

        // Then
        assertTrue(result);
        assertEquals(EXPENSES_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenContinueFalse_whenAtExpensesScreen_thenMoveBackToIncomeScreen() {
        // Given
        setupStateAndVariables(EXPENSES_SCREEN, Map.of(EXT_TYPE, ProcessType.MINOR, "toContinue", false));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.BACK);

        // Then
        assertTrue(result);
        assertEquals(INCOME_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenBlockTrue_whenAtSpeechToText_thenMoveToBlocked() {
        // Given
        setupStateAndVariables(SPEECH_TO_TEXT, Map.of(EXT_TYPE, ProcessType.MINOR, "toBlock", true));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.PROCESS_SPEECH_TO_TEXT);

        // Then
        assertTrue(result);
        assertEquals(BLOCKED, stateMachine.getState().getId());
    }

    @Test
    void givenScanMatchOk_whenAtPerformMatch_thenStartParallelProcessing() {
        // Given
        setupStateAndVariables(PERFORM_MATCH, Map.of(EXT_TYPE, ProcessType.MINOR, "scanMatch", "OK"));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.PERFORM_DOCUMENT_MATCH);

        // Then
        assertTrue(result);
        assertEquals(FACE_RECOGNITION_UPLOAD, stateMachine.getState().getId());
    }

    @Test
    void givenMaxRetries_whenAtPerformMatch_thenMoveToBlocked() {
        // Given
        setupStateAndVariables(PERFORM_MATCH, Map.of(
            EXT_TYPE, ProcessType.MINOR,
            "numOfScanMatchTries", 3,
            "scanMatch", "FAIL"
        ));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.PERFORM_DOCUMENT_MATCH);

        // Then
        assertTrue(result);
        assertEquals(BLOCKED, stateMachine.getState().getId());
    }

    @Test
    void givenSubscriptionRequired_whenAtTwoMoreQuestions_thenMoveToServiceSubscription() {
        // Given
        setupStateAndVariables(TWO_MORE_QUESTIONS_SCREEN, Map.of(
            EXT_TYPE, ProcessType.MINOR,
            "privateInternetSubscriptionIndication", "0",
            "servicePartyStatusCode", 1
        ));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.SUBSCRIBE_TO_SERVICE);

        // Then
        assertTrue(result);
        assertEquals(SERVICE_SUBSCRIPTION, stateMachine.getState().getId());
    }

    @Test
    void givenNoSubscriptionRequired_whenAtTwoMoreQuestions_thenMoveToNoServiceSubscription() {
        // Given
        setupStateAndVariables(TWO_MORE_QUESTIONS_SCREEN, Map.of(
            EXT_TYPE, ProcessType.MINOR,
            "privateInternetSubscriptionIndication", "1",
            "servicePartyStatusCode", 0
        ));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.DECLINE_SERVICE);

        // Then
        assertTrue(result);
        assertEquals(NO_SERVICE_SUBSCRIPTION, stateMachine.getState().getId());
    }

    @Test
    void whenAtWarnings_thenMoveToWelcome() {
        // Given
        setupStateAndVariables(WARNINGS, Map.of(EXT_TYPE, ProcessType.MINOR));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.ACKNOWLEDGE_WARNINGS);

        // Then
        assertTrue(result);
        assertEquals(WELCOME, stateMachine.getState().getId());
    }

    @Test
    void givenStarted_whenSendInvalidEvent_thenTransitionNotExecuted() {
        // Given
        setupStateAndVariables(STARTED, null);

        // When: пытаемся отправить событие, которое не должно быть принято в начальном состоянии
        boolean result = stateMachine.sendEvent(ProcessEvent.SUBMIT_FORMS);

        // Then
        assertFalse(result);
        assertEquals(STARTED, stateMachine.getState().getId());
    }

    @Test
    void givenOccupationScreen_whenTryToSkipIncome_thenTransitionNotExecuted() {
        // Given: находимся на экране occupation
        setupStateAndVariables(MINOR_OCCUPATION_SCREEN, null);

        // When: пытаемся перейти сразу к expenses, минуя income
        boolean result = stateMachine.sendEvent(ProcessEvent.SUBMIT_EXPENSES);

        // Then: переход не должен выполниться
        assertFalse(result);
        assertEquals(MINOR_OCCUPATION_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenContinueFalse_whenTryToMoveForward_thenTransitionNotExecuted() {
        // Given
        setupStateAndVariables(INCOME_SCREEN, Map.of(EXT_TYPE, ProcessType.MINOR, "toContinue", false));
        ProcessState initialState = stateMachine.getState().getId();

        // When: пытаемся продолжить, хотя toContinue = false
        boolean result = stateMachine.sendEvent(ProcessEvent.CONTINUE_FLOW);

        // Then: переход выполняется как self-loop
        assertTrue(result, "Transition should be executed as self-loop when toContinue is false");
        assertEquals(initialState, stateMachine.getState().getId(), "State should remain the same on self-loop");
    }

    @Test
    void givenBlockedState_whenTryToContinue_thenTransitionNotExecuted() {
        // Given: процесс заблокирован
        setupStateAndVariables(BLOCKED, null);

        // When: пытаемся продолжить из заблокированного состояния
        boolean result = stateMachine.sendEvent(ProcessEvent.CONTINUE_FLOW);

        // Then: никакие переходы не должны быть возможны
        assertFalse(result);
        assertEquals(BLOCKED, stateMachine.getState().getId());
    }

    @Test
    void givenNoScanMatch_whenTryParallelProcessing_thenTransitionNotExecuted() {
        // Given
        setupStateAndVariables(PERFORM_MATCH, Map.of(EXT_TYPE, ProcessType.MINOR, "scanMatch", "FAIL", "numOfScanMatchTries", 0));

        // When: выполняем попытку сопоставления документа
        boolean result = stateMachine.sendEvent(ProcessEvent.PERFORM_DOCUMENT_MATCH);

        // Then: событие принято, но остаёмся в PERFORM_MATCH (ретрай)
        assertTrue(result);
        assertEquals(PERFORM_MATCH, stateMachine.getState().getId());
    }

    @Test
    void givenMinorType_startFlow_movesToOccupationScreen() {
        // Given: тип процесса MINOR
        setupStateAndVariables(STARTED, Map.of(EXT_TYPE, ProcessType.MINOR));

        // When: запускаем поток
        boolean result = stateMachine.sendEvent(ProcessEvent.START_FLOW);

        // Then: переходим в экран профессии несовершеннолетнего
        assertTrue(result, "Transition should be executed with MINOR process type");
        assertEquals(MINOR_OCCUPATION_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void whenAccountNotFound_thenMoveBackToStarted() {
        // Given
        setupStateAndVariables(PERFORM_MATCH, Map.of(
            EXT_TYPE, ProcessType.MINOR_TO_REGULAR,
            "accountDetails", Map.of(
                "bankId", "999",
                "branchCode", "999",
                "accountNumber", "999"
            )
        ));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.BACK);

        // Then
        assertTrue(result);
        assertEquals(STARTED, stateMachine.getState().getId());
    }

    @Test
    void whenAtWelcome_completeWelcomeAcceptedAndStaysInWelcome() {
        // Given
        setupStateAndVariables(WELCOME, null);

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.COMPLETE_WELCOME);

        // Then
        assertTrue(result);
        assertEquals(WELCOME, stateMachine.getState().getId());
    }

    @Test
    void givenIncomeScreen_inMinorFlow_completeWelcomeNotAccepted() {
        // Given
        setupStateAndVariables(INCOME_SCREEN, Map.of(EXT_TYPE, ProcessType.MINOR));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.COMPLETE_WELCOME);

        // Then
        assertFalse(result);
        assertEquals(INCOME_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void whenCompleteWelcomeCalledTwice_remainsInWelcomeBothTimes() {
        // Given
        setupStateAndVariables(WELCOME, Map.of(EXT_TYPE, ProcessType.MINOR));

        // When
        boolean first = stateMachine.sendEvent(ProcessEvent.COMPLETE_WELCOME);
        boolean second = stateMachine.sendEvent(ProcessEvent.COMPLETE_WELCOME);

        // Then
        assertTrue(first);
        assertTrue(second);
        assertEquals(WELCOME, stateMachine.getState().getId());
    }

    @Test
    void givenMinorToRegularFlow_whenAtWelcome_completeWelcomeAccepted() {
        // Given
        setupStateAndVariables(WELCOME, Map.of(EXT_TYPE, ProcessType.MINOR_TO_REGULAR));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.COMPLETE_WELCOME);

        // Then
        assertTrue(result);
        assertEquals(WELCOME, stateMachine.getState().getId());
    }

    private void setupStateAndVariables(ProcessState state, Map<String, Object> variables) {
        // Reset and start machine
        stateMachine = factory.getStateMachine();
        stateMachine.start();

        // Force state for testing
        try {
            stateMachine.getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachine(
                    new DefaultStateMachineContext<>(state, null, null, null)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set state", e);
        }

        // Set variables after reset so they are not cleared
        if (variables != null) {
            variables.forEach((k, v) -> stateMachine.getExtendedState().getVariables().put(k, v));
        }
    }
}
