package com.example.state_machine.service;

import com.example.state_machine.config.TestMongoConfig;
import com.example.state_machine.config.TestStateMachineConfig;
import com.example.state_machine.model.*;
import com.example.state_machine.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.test.StateMachineTestPlan;
import org.springframework.statemachine.test.StateMachineTestPlanBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Import({TestStateMachineConfig.class, TestMongoConfig.class})
class FlowServiceTest {

    @Mock
    private ProcessInstanceRepository repository;

    @Autowired
    private FlowService flowService;

    @Autowired
    private StateMachineFactory<ProcessState, ProcessEvent> stateMachineFactory;

    @Mock
    private StateMachine<ProcessState, ProcessEvent> stateMachine;

    @Mock
    private State<ProcessState, ProcessEvent> state;

    @Captor
    private ArgumentCaptor<ProcessInstance> processInstanceCaptor;

    @Test
    void startProcess_CreatesNewInstance_WithSingleOwnerType() {
        Map<String, Object> initialData = Map.of("accountType", "CHECKING");
        String clientId = "client123";
        ProcessType type = ProcessType.SINGLE_OWNER;

        ProcessInstance expectedInstance = ProcessInstance.builder()
                .id("1")
                .clientId(clientId)
                .type(type)
                .state(ProcessState.STARTED)
                .variables(initialData)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.save(any(ProcessInstance.class))).thenReturn(expectedInstance);

        ProcessInstance result = flowService.startProcess(clientId, type, initialData);

        assertEquals(ProcessState.STARTED, result.getState());
        assertEquals(type, result.getType());
        assertEquals(clientId, result.getClientId());
        assertEquals(initialData, result.getVariables());
    }

    @Test
    void startProcess_CreatesNewInstance_WithMinorType() {
        Map<String, Object> initialData = Map.of("parentId", "parent123");

        flowService.startProcess("minor123", ProcessType.MINOR, initialData);

        verify(repository).save(processInstanceCaptor.capture());
        ProcessInstance savedInstance = processInstanceCaptor.getValue();

        assertEquals(ProcessState.STARTED, savedInstance.getState());
        assertEquals(ProcessType.MINOR, savedInstance.getType());
        assertEquals(initialData, savedInstance.getVariables());
    }

    @Test
    void startProcess_CreatesNewInstance_WithEmptyInitialData() {
        flowService.startProcess("client123", ProcessType.SINGLE_OWNER, null);

        verify(repository).save(processInstanceCaptor.capture());
        ProcessInstance savedInstance = processInstanceCaptor.getValue();

        assertEquals(Map.of(), savedInstance.getVariables());
    }

    @Test
    void startProcess_ThrowsException_WhenClientIdIsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            flowService.startProcess("", ProcessType.SINGLE_OWNER, Map.of())
        );
    }

    @Test
    void startProcess_ThrowsException_WhenTypeIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
            flowService.startProcess("client123", null, Map.of())
        );
    }

    @Test
    void handleEvent_UpdatesState_WhenEventAccepted() {
        ProcessInstance existingInstance = ProcessInstance.builder()
                .id("123")
                .clientId("client123")
                .type(ProcessType.SINGLE_OWNER)
                .state(ProcessState.STARTED)
                .variables(Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findById("123")).thenReturn(Optional.of(existingInstance));
        when(stateMachineFactory.getStateMachine("123")).thenReturn(stateMachine);
        when(stateMachine.sendEvent(ProcessEvent.SUBMIT_PERSONAL)).thenReturn(true);
        when(stateMachine.getState()).thenReturn(state);
        when(state.getId()).thenReturn(ProcessState.FILL_PERSONAL_DETAILS);

        Map<String, Object> data = Map.of("firstName", "John", "lastName", "Doe");
        ProcessInstance updatedInstance = flowService.handleEvent("123", ProcessEvent.SUBMIT_PERSONAL, data);

        assertEquals(ProcessState.FILL_PERSONAL_DETAILS, updatedInstance.getState());
        assertEquals(data, updatedInstance.getVariables());
        assertTrue(updatedInstance.getUpdatedAt().isAfter(updatedInstance.getCreatedAt()));
    }

    @Test
    void handleEvent_ThrowsException_WhenEventNotAccepted() {
        ProcessInstance existingInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.STARTED)
                .build();

        when(repository.findById("123")).thenReturn(Optional.of(existingInstance));
        when(stateMachineFactory.getStateMachine("123")).thenReturn(stateMachine);
        when(stateMachine.sendEvent(ProcessEvent.CREATE_ACCOUNT)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
            flowService.handleEvent("123", ProcessEvent.CREATE_ACCOUNT, Map.of())
        );
    }

    @Test
    void handleEvent_MergesVariables_WhenEventAccepted() {
        Map<String, Object> existingVariables = Map.of("accountType", "CHECKING");
        ProcessInstance existingInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.STARTED)
                .variables(existingVariables)
                .createdAt(Instant.now().minusSeconds(60))
                .updatedAt(Instant.now().minusSeconds(60))
                .build();

        when(repository.findById("123")).thenReturn(Optional.of(existingInstance));
        when(stateMachineFactory.getStateMachine("123")).thenReturn(stateMachine);
        when(stateMachine.sendEvent(ProcessEvent.SUBMIT_PERSONAL)).thenReturn(true);
        when(stateMachine.getState()).thenReturn(state);
        when(state.getId()).thenReturn(ProcessState.FILL_PERSONAL_DETAILS);

        Map<String, Object> newData = Map.of("firstName", "John", "lastName", "Doe");
        ProcessInstance updatedInstance = flowService.handleEvent("123", ProcessEvent.SUBMIT_PERSONAL, newData);

        assertTrue(updatedInstance.getVariables().containsKey("accountType"));
        assertTrue(updatedInstance.getVariables().containsKey("firstName"));
        assertTrue(updatedInstance.getVariables().containsKey("lastName"));
        assertTrue(updatedInstance.getUpdatedAt().isAfter(existingInstance.getUpdatedAt()));
    }

    @Test
    void handleEvent_UpdatesState_WhenKYCVerified() throws Exception {
        String processId = "123";
        ProcessInstance existingInstance = ProcessInstance.builder()
                .id(processId)
                .clientId("client123")
                .type(ProcessType.SINGLE_OWNER)
                .state(ProcessState.KYC_IN_PROGRESS)
                .variables(Map.of("accountType", "CHECKING"))
                .build();

        Map<String, Object> kycData = Map.of(
            "verificationId", "kyc123",
            "status", "APPROVED",
            "riskLevel", "LOW"
        );

        when(repository.findById(processId)).thenReturn(Optional.of(existingInstance));
        when(repository.save(any(ProcessInstance.class))).thenAnswer(i -> i.getArgument(0));

        StateMachine<ProcessState, ProcessEvent> stateMachine = stateMachineFactory.getStateMachine(processId);
        stateMachine.start();

        StateMachineTestPlan<ProcessState, ProcessEvent> plan =
                StateMachineTestPlanBuilder.<ProcessState, ProcessEvent>builder()
                        .stateMachine(stateMachine)
                        .step()
                            .expectState(ProcessState.STARTED)
                            .and()
                        .step()
                            .sendEvent(ProcessEvent.KYC_VERIFIED)
                            .expectStateChanged(1)
                            .expectState(ProcessState.WAITING_FOR_BIOMETRY)
                            .and()
                        .build();
        plan.test();

        ProcessInstance updatedInstance = flowService.handleEvent(processId, ProcessEvent.KYC_VERIFIED, kycData);

        assertEquals(ProcessState.WAITING_FOR_BIOMETRY, updatedInstance.getState());
        assertEquals("APPROVED", updatedInstance.getVariables().get("status"));
        assertEquals("LOW", updatedInstance.getVariables().get("riskLevel"));
    }

    @Test
    void handleEvent_UpdatesState_WhenBiometryVerified() throws Exception {
        String processId = "123";
        ProcessInstance existingInstance = ProcessInstance.builder()
                .id(processId)
                .clientId("client123")
                .type(ProcessType.SINGLE_OWNER)
                .state(ProcessState.WAITING_FOR_BIOMETRY)
                .variables(Map.of("kycStatus", "APPROVED"))
                .build();

        Map<String, Object> biometryData = Map.of(
            "biometryId", "bio123",
            "matchScore", "0.95",
            "livenessScore", "0.98"
        );

        when(repository.findById(processId)).thenReturn(Optional.of(existingInstance));
        when(repository.save(any(ProcessInstance.class))).thenAnswer(i -> i.getArgument(0));

        StateMachine<ProcessState, ProcessEvent> stateMachine = stateMachineFactory.getStateMachine(processId);
        stateMachine.start();

        StateMachineTestPlan<ProcessState, ProcessEvent> plan =
                StateMachineTestPlanBuilder.<ProcessState, ProcessEvent>builder()
                        .stateMachine(stateMachine)
                        .step()
                            .expectState(ProcessState.STARTED)
                            .and()
                        .step()
                            .sendEvent(ProcessEvent.BIOMETRY_SUCCESS)
                            .expectStateChanged(1)
                            .expectState(ProcessState.BIOMETRY_VERIFIED)
                            .and()
                        .build();
        plan.test();

        ProcessInstance updatedInstance = flowService.handleEvent(processId, ProcessEvent.BIOMETRY_SUCCESS, biometryData);

        assertEquals(ProcessState.BIOMETRY_VERIFIED, updatedInstance.getState());
        assertEquals("0.95", updatedInstance.getVariables().get("matchScore"));
        assertEquals("0.98", updatedInstance.getVariables().get("livenessScore"));
    }

    @Test
    void handleEvent_UpdatesState_WhenParentConsentReceived() throws Exception {
        String processId = "123";
        ProcessInstance existingInstance = ProcessInstance.builder()
                .id(processId)
                .clientId("minor123")
                .type(ProcessType.MINOR)
                .state(ProcessState.WAITING_FOR_PARENT_CONSENT)
                .variables(Map.of("age", "14"))
                .build();

        Map<String, Object> consentData = Map.of(
            "parentId", "parent123",
            "consentDocument", "consent.pdf",
            "consentDate", "2025-08-09"
        );

        when(repository.findById(processId)).thenReturn(Optional.of(existingInstance));
        when(repository.save(any(ProcessInstance.class))).thenAnswer(i -> i.getArgument(0));

        StateMachine<ProcessState, ProcessEvent> stateMachine = stateMachineFactory.getStateMachine(processId);
        stateMachine.start();

        StateMachineTestPlan<ProcessState, ProcessEvent> plan =
                StateMachineTestPlanBuilder.<ProcessState, ProcessEvent>builder()
                        .stateMachine(stateMachine)
                        .step()
                            .expectState(ProcessState.STARTED)
                            .and()
                        .step()
                            .sendEvent(ProcessEvent.PARENT_APPROVED)
                            .expectStateChanged(1)
                            .expectState(ProcessState.ACCOUNT_CREATED_LIMITED)
                            .and()
                        .build();
        plan.test();

        ProcessInstance updatedInstance = flowService.handleEvent(processId, ProcessEvent.PARENT_APPROVED, consentData);

        assertEquals(ProcessState.ACCOUNT_CREATED_LIMITED, updatedInstance.getState());
        assertEquals("parent123", updatedInstance.getVariables().get("parentId"));
        assertEquals("consent.pdf", updatedInstance.getVariables().get("consentDocument"));
    }

    @Test
    void handleEvent_ThrowsException_WhenProcessNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
            flowService.handleEvent("nonexistent", ProcessEvent.SUBMIT_PERSONAL, Map.of())
        );
    }
}
