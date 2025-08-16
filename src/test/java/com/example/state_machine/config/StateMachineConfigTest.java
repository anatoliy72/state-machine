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
        setupStateAndVariables(INCOME_SCREEN, Map.of("toContinue", true));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.CONTINUE_FLOW);

        // Then
        assertTrue(result);
        assertEquals(EXPENSES_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenContinueFalse_whenAtExpensesScreen_thenMoveBackToIncomeScreen() {
        // Given
        setupStateAndVariables(EXPENSES_SCREEN, Map.of("toContinue", false));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.BACK);

        // Then
        assertTrue(result);
        assertEquals(INCOME_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenBlockTrue_whenAtSpeechToText_thenMoveToBlocked() {
        // Given
        setupStateAndVariables(SPEECH_TO_TEXT, Map.of("toBlock", true));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.BLOCK_FLOW);

        // Then
        assertTrue(result);
        assertEquals(BLOCKED, stateMachine.getState().getId());
    }

    @Test
    void givenScanMatchOk_whenAtPerformMatch_thenStartParallelProcessing() {
        // Given
        setupStateAndVariables(PERFORM_MATCH, Map.of("scanMatch", "OK"));

        // When
        stateMachine.sendEvent(ProcessEvent.UPLOAD_FACE_RECOGNITION);
        stateMachine.sendEvent(ProcessEvent.VALIDATE_CUSTOMER_INFO);

        // Then
        assertTrue(stateMachine.getState().getId() == FACE_RECOGNITION_UPLOAD ||
                  stateMachine.getState().getId() == CUSTOMER_INFO_VALIDATION);
    }

    @Test
    void givenMaxRetries_whenAtPerformMatch_thenMoveToBlocked() {
        // Given
        setupStateAndVariables(PERFORM_MATCH, Map.of(
            "numOfScanMatchTries", 3,
            "scanMatch", "FAIL"
        ));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.BLOCK_FLOW);

        // Then
        assertTrue(result);
        assertEquals(BLOCKED, stateMachine.getState().getId());
    }

    @Test
    void givenSubscriptionRequired_whenAtTwoMoreQuestions_thenMoveToServiceSubscription() {
        // Given
        setupStateAndVariables(TWO_MORE_QUESTIONS_SCREEN, Map.of(
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
        setupStateAndVariables(WARNINGS, null);

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
        setupStateAndVariables(INCOME_SCREEN, Map.of("toContinue", false));
        ProcessState initialState = stateMachine.getState().getId();

        // When: пытаемся продолжить, хотя toContinue = false
        boolean result = stateMachine.sendEvent(ProcessEvent.CONTINUE_FLOW);

        // Then: проверяем что состояние не изменилось
        assertEquals(initialState, stateMachine.getState().getId(), "State should not change when toContinue is false");
        assertFalse(result, "Transition should not be executed when toContinue is false");
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
        setupStateAndVariables(PERFORM_MATCH, Map.of("scanMatch", "FAIL"));

        // When: пытаемся начать параллельные процессы без успешного сканирования
        boolean result = stateMachine.sendEvent(ProcessEvent.UPLOAD_FACE_RECOGNITION);

        // Then
        assertFalse(result);
        assertEquals(PERFORM_MATCH, stateMachine.getState().getId());
    }

//    @Test
//    void givenWrongProcessType_whenStartFlow_thenTransitionNotExecuted() {
//        // Given: неверный тип процесса
//        stateMachine.getExtendedState().getVariables().put(EXT_TYPE, ProcessType.SINGLE_OWNER);
//
//        // When
//        boolean result = stateMachine.sendEvent(ProcessEvent.START_FLOW);
//
//        // Then
//        assertFalse(result);
//        assertEquals(STARTED, stateMachine.getState().getId());
//    }

    @Test
    void givenVideoScreen_whenTryToSkipToForms_thenTransitionNotExecuted() {
        // Given
        setupStateAndVariables(VIDEO_SCREEN, null);

        // When: пытаемся пропустить несколько шагов и перейти сразу к формам
        boolean result = stateMachine.sendEvent(ProcessEvent.SUBMIT_FORMS);

        // Then
        assertFalse(result);
        assertEquals(VIDEO_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenWelcomeState_whenTryAnyTransition_thenTransitionNotExecuted() {
        // Given: процесс завершен
        setupStateAndVariables(WELCOME, null);

        // When: пытаемся выполнить любой переход из конечного состояния
        boolean result1 = stateMachine.sendEvent(ProcessEvent.CONTINUE_FLOW);
        boolean result2 = stateMachine.sendEvent(ProcessEvent.BACK);
        boolean result3 = stateMachine.sendEvent(ProcessEvent.START_FLOW);

        // Then: никакие переходы не должны быть возможны
        assertFalse(result1);
        assertFalse(result2);
        assertFalse(result3);
        assertEquals(WELCOME, stateMachine.getState().getId());
    }

    @Test
    void givenMinorToRegularType_whenStartFlow_thenMoveToIncomeScreen() {
        // Given
        stateMachine.getExtendedState().getVariables().put(EXT_TYPE, ProcessType.MINOR_TO_REGULAR);

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.START_FLOW);

        // Then
        assertTrue(result);
        assertEquals(INCOME_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenIncomeScreen_whenSubmitIncome_thenMoveToPerformMatch() {
        // Given
        setupStateAndVariables(INCOME_SCREEN, Map.of(EXT_TYPE, ProcessType.MINOR_TO_REGULAR));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.SUBMIT_INCOME);

        // Then
        assertTrue(result);
        assertEquals(PERFORM_MATCH, stateMachine.getState().getId());
    }

    @Test
    void givenAccountExists_whenPerformMatch_thenMoveToWelcome() {
        // Given
        Map<String, String> accountDetails = Map.of(
            "bankId", "12",
            "branchCode", "123",
            "accountNumber", "1234567"
        );
        Map<String, Object> variables = new HashMap<>();
        variables.put(EXT_TYPE, ProcessType.MINOR_TO_REGULAR);
        variables.put("accountDetails", accountDetails);

        setupStateAndVariables(PERFORM_MATCH, variables);

        // When
        System.out.println("Current state before event: " + stateMachine.getState().getId());
        System.out.println("Account details: " + accountDetails);
        System.out.println("Process type: " + stateMachine.getExtendedState().getVariables().get(EXT_TYPE));

        boolean result = stateMachine.sendEvent(ProcessEvent.PERFORM_DOCUMENT_MATCH);

        // Then
        System.out.println("Event accepted: " + result);
        System.out.println("Final state: " + stateMachine.getState().getId());
        assertTrue(result, "Event should be accepted");
        assertEquals(WELCOME, stateMachine.getState().getId(), "Should move to WELCOME state when account exists");
    }

    @Test
    void givenNoAccountExists_whenPerformMatch_thenMoveBackToStarted() {
        // Given
        setupStateAndVariables(PERFORM_MATCH, Map.of(
            EXT_TYPE, ProcessType.MINOR_TO_REGULAR,
            "bankBranchAccountExists", false
        ));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.BACK);

        // Then
        assertTrue(result);
        assertEquals(STARTED, stateMachine.getState().getId());
    }

    @Test
    void givenWrongType_whenStartMinorToRegularFlow_thenTransitionNotExecuted() {
        // Given: неверный тип процесса
        setupStateAndVariables(STARTED, Map.of(EXT_TYPE, ProcessType.MINOR));

        // When: пытаемся начать процесс MINOR_TO_REGULAR с неверным типом
        boolean result = stateMachine.sendEvent(ProcessEvent.START_FLOW);

        // Then
        assertFalse(result, "Transition should not be executed with wrong process type");
        assertEquals(STARTED, stateMachine.getState().getId());
    }

    @Test
    void givenIncomeScreen_whenTryToSkipMatch_thenTransitionNotExecuted() {
        // Given
        setupStateAndVariables(INCOME_SCREEN, Map.of(EXT_TYPE, ProcessType.MINOR_TO_REGULAR));

        // When: пытаемся пропустить PERFORM_MATCH и перейти сразу к WELCOME
        boolean result = stateMachine.sendEvent(ProcessEvent.COMPLETE_WELCOME);

        // Then
        assertFalse(result, "Should not be able to skip PERFORM_MATCH state");
        assertEquals(INCOME_SCREEN, stateMachine.getState().getId());
    }

    @Test
    void givenPerformMatch_whenInvalidEvent_thenTransitionNotExecuted() {
        // Given
        setupStateAndVariables(PERFORM_MATCH, Map.of(
            EXT_TYPE, ProcessType.MINOR_TO_REGULAR,
            "bankBranchAccountExists", true
        ));

        // When: пытаемся отправить неверное событие
        boolean result = stateMachine.sendEvent(ProcessEvent.SUBMIT_OCCUPATION);

        // Then
        assertFalse(result, "Invalid event should not trigger transition");
        assertEquals(PERFORM_MATCH, stateMachine.getState().getId());
    }

    @Test
    void givenStarted_whenTryDirectlyToWelcome_thenTransitionNotExecuted() {
        // Given
        setupStateAndVariables(STARTED, Map.of(EXT_TYPE, ProcessType.MINOR_TO_REGULAR));

        // When: пытаемся перейти сразу к WELCOME
        boolean result = stateMachine.sendEvent(ProcessEvent.COMPLETE_WELCOME);

        // Then
        assertFalse(result, "Should not be able to skip directly to WELCOME");
        assertEquals(STARTED, stateMachine.getState().getId());
    }

    @Test
    void whenAccountNotFound_thenMoveBackToStarted() {
        // Given
        stateMachine.getExtendedState().getVariables().put(EXT_TYPE, ProcessType.MINOR_TO_REGULAR);
        setupStateAndVariables(PERFORM_MATCH, Map.of(
            "accountDetails", Map.of(
                "bankId", "999",        // несуществующий банк
                "branchCode", "999",    // несуществующий филиал
                "accountNumber", "999"   // несуществующий счет
            )
        ));

        // When
        boolean result = stateMachine.sendEvent(ProcessEvent.BACK);

        // Then
        assertTrue(result);
        assertEquals(STARTED, stateMachine.getState().getId());
    }

    private void setupStateAndVariables(ProcessState state, Map<String, Object> variables) {
        // Reset and start machine
        stateMachine = factory.getStateMachine();
        stateMachine.start();

        // Set variables first
        if (variables != null) {
            variables.forEach((k, v) -> stateMachine.getExtendedState().getVariables().put(k, v));
        }

        // Force state for testing
        try {
            ((StateMachine<ProcessState, ProcessEvent>) stateMachine).getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachine(
                    new DefaultStateMachineContext<>(state, null, null, null)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set state", e);
        }
    }
}
