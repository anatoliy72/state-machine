package com.example.state_machine.controller;

import com.example.state_machine.exception.GlobalExceptionHandler;
import com.example.state_machine.model.*;
import com.example.state_machine.controller.dto.*;
import com.example.state_machine.service.FlowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProcessControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private FlowService flowService;

    @InjectMocks
    private ProcessController processController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(processController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void start_ReturnsProcessInstance_WhenSingleOwnerAccountOpening() throws Exception {
        // Given
        Map<String, Object> initialData = Map.of(
                "accountType", "CHECKING",
                "currency", "USD"
        );
        StartRequest request = StartRequest.builder()
                .clientId("client123")
                .type(ProcessType.SINGLE_OWNER)
                .initialData(initialData)
                .build();

        ProcessInstance expectedInstance = ProcessInstance.builder()
                .id("1")
                .state(ProcessState.STARTED)
                .type(ProcessType.SINGLE_OWNER)
                .build();

        when(flowService.startProcess(eq("client123"), eq(ProcessType.SINGLE_OWNER), eq(initialData)))
                .thenReturn(expectedInstance);

        // When & Then
        mockMvc.perform(post("/process/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STARTED"))
                .andExpect(jsonPath("$.type").value("SINGLE_OWNER"));
    }

    @Test
    void start_ReturnsProcessInstance_WhenMinorAccountOpening() throws Exception {
        Map<String, Object> initialData = Map.of(
                "age", "14",
                "parentId", "parent123"
        );
        StartRequest request = StartRequest.builder()
                .clientId("minor123")
                .type(ProcessType.MINOR)
                .initialData(initialData)
                .build();

        ProcessInstance expectedInstance = ProcessInstance.builder()
                .id("2")
                .state(ProcessState.STARTED)
                .type(ProcessType.MINOR)
                .build();

        when(flowService.startProcess(eq("minor123"), eq(ProcessType.MINOR), eq(initialData)))
                .thenReturn(expectedInstance);

        mockMvc.perform(post("/process/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STARTED"))
                .andExpect(jsonPath("$.type").value("MINOR"));
    }

    @Test
    void start_ReturnsBadRequest_WhenClientIdIsBlank() throws Exception {
        StartRequest request = StartRequest.builder()
                .clientId("")
                .type(ProcessType.SINGLE_OWNER)
                .build();

        mockMvc.perform(post("/process/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void start_ReturnsBadRequest_WhenTypeIsNull() throws Exception {
        StartRequest request = StartRequest.builder()
                .clientId("client123")
                .type(null)
                .build();

        mockMvc.perform(post("/process/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_ReturnsProcessInstance_WhenExists() throws Exception {
        ProcessInstance instance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.FILL_PERSONAL_DETAILS)
                .type(ProcessType.SINGLE_OWNER)
                .build();

        when(flowService.getProcess("123")).thenReturn(instance);

        mockMvc.perform(get("/process/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"))
                .andExpect(jsonPath("$.state").value("FILL_PERSONAL_DETAILS"));
    }

    @Test
    void get_ReturnsNotFound_WhenProcessDoesNotExist() throws Exception {
        when(flowService.getProcess("nonexistent"))
                .thenThrow(new NoSuchElementException("Process not found"));

        mockMvc.perform(get("/process/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Process not found"));
    }

    @Test
    void event_ReturnsUpdatedInstance_WhenSubmittingPersonalDetails() throws Exception {
        Map<String, Object> data = Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "dateOfBirth", "1990-01-01"
        );

        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.SUBMIT_PERSONAL)
                .data(data)
                .build();

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.SUBMIT_PERSONAL), eq(data)))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANSWER_ACCOUNT_QUESTIONS"));
    }

    @Test
    void event_ReturnsUpdatedInstance_WhenKYCVerified() throws Exception {
        Map<String, Object> data = Map.of(
                "verificationId", "kyc123",
                "status", "APPROVED",
                "riskLevel", "LOW"
        );

        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.KYC_VERIFIED)
                .data(data)
                .build();

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.WAITING_FOR_BIOMETRY)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.KYC_VERIFIED), eq(data)))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WAITING_FOR_BIOMETRY"));
    }

    @Test
    void event_ReturnsUpdatedInstance_WhenParentConsentReceived() throws Exception {
        Map<String, Object> data = Map.of(
                "parentId", "parent123",
                "consentDocument", "consent.pdf",
                "consentStatus", "APPROVED"
        );

        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.PARENT_APPROVED)
                .data(data)
                .build();

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.ACCOUNT_CREATED_LIMITED)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.PARENT_APPROVED), eq(data)))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCOUNT_CREATED_LIMITED"));
    }

    @Test
    void event_ReturnsBadRequest_WhenEventIsNull() throws Exception {
        EventRequest request = EventRequest.builder()
                .event(null)
                .data(new HashMap<>())
                .build();

        mockMvc.perform(post("/process/123/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void event_ReturnsNotFound_WhenProcessIdDoesNotExist() throws Exception { // renamed for accuracy
        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.SUBMIT_PERSONAL)
                .data(Map.of("firstName", "John"))
                .build();

        when(flowService.handleEvent(eq("nonexistent"), any(), any()))
                .thenThrow(new NoSuchElementException("Process not found"));

        mockMvc.perform(post("/process/nonexistent/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void asyncResult_ReturnsUpdatedInstance_WhenKYCCompleted() throws Exception {
        AsyncResultRequest request = new AsyncResultRequest();
        request.setType("kyc");
        request.setResult(Map.of(
                "status", "APPROVED",
                "verificationId", "kyc123",
                "riskLevel", "LOW"
        ));

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.WAITING_FOR_BIOMETRY)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.KYC_VERIFIED), any()))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/async-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WAITING_FOR_BIOMETRY"));
    }

    @Test
    void asyncResult_ReturnsUpdatedInstance_WhenBiometryCompleted() throws Exception {
        AsyncResultRequest request = new AsyncResultRequest();
        request.setType("biometry");
        request.setResult(Map.of(
                "status", "SUCCESS",
                "biometryId", "bio123",
                "matchScore", "0.95",
                "livenessScore", "0.98"
        ));

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.BIOMETRY_VERIFIED)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.BIOMETRY_SUCCESS), any()))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/async-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("BIOMETRY_VERIFIED"));
    }

    @Test
    void asyncResult_ReturnsBadRequest_WhenTypeIsBlank() throws Exception {
        AsyncResultRequest request = new AsyncResultRequest();
        request.setType("");
        request.setResult(Map.of());

        mockMvc.perform(post("/process/123/async-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void asyncResult_ReturnsBadRequest_WhenResultIsNull() throws Exception {
        AsyncResultRequest request = new AsyncResultRequest();
        request.setType("kyc");
        request.setResult(null);

        mockMvc.perform(post("/process/123/async-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void asyncResult_ReturnsBadRequest_WhenTypeIsUnknown() throws Exception {
        AsyncResultRequest request = new AsyncResultRequest();
        request.setType("unknown");
        request.setResult(Map.of("status", "SUCCESS"));

        mockMvc.perform(post("/process/123/async-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------- New tests for conversion flow ----------

    @Test
    void startConversion_ReturnsProcessInstance_WhenValid() throws Exception {
        StartConversionRequest req = StartConversionRequest.builder()
                .clientId("client-conv-1")
                .minorAccountId("minor-acc-123")
                .initialData(Map.of("reason", "age>=18"))
                .build();

        ProcessInstance expected = ProcessInstance.builder()
                .id("conv-1")
                .type(ProcessType.MINOR_TO_REGULAR)
                .state(ProcessState.MINOR_ACCOUNT_IDENTIFIED)
                .build();

        when(flowService.startMinorToRegularConversion(eq("client-conv-1"), eq("minor-acc-123"), eq(Map.of("reason", "age>=18"))))
                .thenReturn(expected);

        mockMvc.perform(post("/process/conversion/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MINOR_TO_REGULAR"))
                .andExpect(jsonPath("$.state").value("MINOR_ACCOUNT_IDENTIFIED"));
    }

    @Test
    void startConversion_ReturnsBadRequest_WhenClientIdBlank() throws Exception {
        StartConversionRequest req = StartConversionRequest.builder()
                .clientId("") // @NotBlank should trigger
                .minorAccountId("minor-acc-123")
                .initialData(Map.of("reason", "age>=18"))
                .build();

        mockMvc.perform(post("/process/conversion/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
