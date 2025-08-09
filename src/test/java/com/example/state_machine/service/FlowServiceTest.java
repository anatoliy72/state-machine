package com.example.state_machine.service;

import com.example.state_machine.model.*;
import com.example.state_machine.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.access.StateMachineAccess;
import org.springframework.statemachine.access.StateMachineAccessor;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultExtendedState;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowServiceTest {

    @Mock
    private ProcessInstanceRepository repository;

    @Mock
    private StateMachineFactory<ProcessState, ProcessEvent> stateMachineFactory;

    @Mock
    private org.springframework.statemachine.StateMachinePersist<ProcessState, ProcessEvent, String> stateMachinePersist;

    @InjectMocks
    private FlowService flowService;

    @Mock
    private StateMachine<ProcessState, ProcessEvent> stateMachine;

    @Mock
    private State<ProcessState, ProcessEvent> state;

    @Captor
    private ArgumentCaptor<ProcessInstance> processInstanceCaptor;

    // --- helpers to fully mock the SM internals used by FlowService ---
    private void wireStateMachineMock() {
        // Extended state is a real mutable impl so FlowService can put vars/guards
        ExtendedState extended = new DefaultExtendedState();
        when(stateMachine.getExtendedState()).thenReturn(extended);

        // Accessor & region access to support resetStateMachine(...) call
        @SuppressWarnings("unchecked")
        StateMachineAccessor<ProcessState, ProcessEvent> accessor = mock(StateMachineAccessor.class);
        @SuppressWarnings("unchecked")
        StateMachineAccess<ProcessState, ProcessEvent> regionAccess = mock(StateMachineAccess.class);

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<StateMachineAccess<ProcessState, ProcessEvent>> consumer = inv.getArgument(0);
            consumer.accept(regionAccess);
            return null;
        }).when(accessor).doWithAllRegions(any());

        when(stateMachine.getStateMachineAccessor()).thenReturn(accessor);

        // start/stop are void — let them no-op
        // getState will be set per-test via when(stateMachine.getState()).thenReturn(state)
    }

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

        when(repository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        flowService.startProcess("minor123", ProcessType.MINOR, initialData);

        verify(repository).save(processInstanceCaptor.capture());
        ProcessInstance savedInstance = processInstanceCaptor.getValue();

        assertEquals(ProcessState.STARTED, savedInstance.getState());
        assertEquals(ProcessType.MINOR, savedInstance.getType());
        assertEquals(initialData, savedInstance.getVariables());
    }

    @Test
    void startProcess_CreatesNewInstance_WithEmptyInitialData() {
        when(repository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));

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
        wireStateMachineMock();

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
        when(repository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stateMachineFactory.getStateMachine("123")).thenReturn(stateMachine);

        when(stateMachine.sendEvent(ProcessEvent.SUBMIT_PERSONAL)).thenReturn(true);
        when(stateMachine.getState()).thenReturn(state);
        when(state.getId()).thenReturn(ProcessState.FILL_PERSONAL_DETAILS);

        Map<String, Object> data = Map.of("firstName", "John", "lastName", "Doe");

        ProcessInstance updatedInstance = flowService.handleEvent("123", ProcessEvent.SUBMIT_PERSONAL, data);

        assertEquals(ProcessState.FILL_PERSONAL_DETAILS, updatedInstance.getState());
        assertEquals("John", updatedInstance.getVariables().get("firstName"));
        assertEquals("Doe", updatedInstance.getVariables().get("lastName"));
        assertTrue(updatedInstance.getUpdatedAt().isAfter(updatedInstance.getCreatedAt()));
    }

    @Test
    void handleEvent_ThrowsException_WhenEventNotAccepted() {
        wireStateMachineMock();

        ProcessInstance existingInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.STARTED)
                .type(ProcessType.SINGLE_OWNER)
                .variables(Map.of())
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
        wireStateMachineMock();

        Map<String, Object> existingVariables = Map.of("accountType", "CHECKING");
        ProcessInstance existingInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.STARTED)
                .type(ProcessType.SINGLE_OWNER)
                .variables(existingVariables)
                .createdAt(Instant.now().minusSeconds(60))
                .updatedAt(Instant.now().minusSeconds(60))
                .build();

        when(repository.findById("123")).thenReturn(Optional.of(existingInstance));
        when(repository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stateMachineFactory.getStateMachine("123")).thenReturn(stateMachine);

        when(stateMachine.sendEvent(ProcessEvent.SUBMIT_PERSONAL)).thenReturn(true);
        when(stateMachine.getState()).thenReturn(state);
        when(state.getId()).thenReturn(ProcessState.FILL_PERSONAL_DETAILS);

        Map<String, Object> newData = Map.of("firstName", "John", "lastName", "Doe");
        ProcessInstance updatedInstance = flowService.handleEvent("123", ProcessEvent.SUBMIT_PERSONAL, newData);

        assertEquals("CHECKING", updatedInstance.getVariables().get("accountType"));
        assertEquals("John", updatedInstance.getVariables().get("firstName"));
        assertEquals("Doe", updatedInstance.getVariables().get("lastName"));
    }

    @Test
    void handleEvent_UpdatesState_WhenKYCVerified() {
        wireStateMachineMock();

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
        when(repository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stateMachineFactory.getStateMachine(processId)).thenReturn(stateMachine);

        when(stateMachine.sendEvent(ProcessEvent.KYC_VERIFIED)).thenReturn(true);
        when(stateMachine.getState()).thenReturn(state);
        when(state.getId()).thenReturn(ProcessState.WAITING_FOR_BIOMETRY);

        ProcessInstance updatedInstance = flowService.handleEvent(processId, ProcessEvent.KYC_VERIFIED, kycData);

        assertEquals(ProcessState.WAITING_FOR_BIOMETRY, updatedInstance.getState());
        assertEquals("APPROVED", updatedInstance.getVariables().get("status"));
        assertEquals("LOW", updatedInstance.getVariables().get("riskLevel"));
    }

    @Test
    void handleEvent_UpdatesState_WhenBiometryVerified() {
        wireStateMachineMock();

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
        when(repository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stateMachineFactory.getStateMachine(processId)).thenReturn(stateMachine);

        when(stateMachine.sendEvent(ProcessEvent.BIOMETRY_SUCCESS)).thenReturn(true);
        when(stateMachine.getState()).thenReturn(state);
        when(state.getId()).thenReturn(ProcessState.BIOMETRY_VERIFIED);

        ProcessInstance updatedInstance = flowService.handleEvent(processId, ProcessEvent.BIOMETRY_SUCCESS, biometryData);

        assertEquals(ProcessState.BIOMETRY_VERIFIED, updatedInstance.getState());
        assertEquals("0.95", updatedInstance.getVariables().get("matchScore"));
        assertEquals("0.98", updatedInstance.getVariables().get("livenessScore"));
    }

    @Test
    void handleEvent_UpdatesState_WhenParentConsentReceived() {
        wireStateMachineMock();

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
        when(repository.save(any(ProcessInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stateMachineFactory.getStateMachine(processId)).thenReturn(stateMachine);

        when(stateMachine.sendEvent(ProcessEvent.PARENT_APPROVED)).thenReturn(true);
        when(stateMachine.getState()).thenReturn(state);
        when(state.getId()).thenReturn(ProcessState.ACCOUNT_CREATED_LIMITED);

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
