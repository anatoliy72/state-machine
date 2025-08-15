package com.example.state_machine.controller;

import com.example.state_machine.controller.dto.AsyncResultRequest;
import com.example.state_machine.controller.dto.EventRequest;
import com.example.state_machine.controller.dto.StartRequest;
import com.example.state_machine.exception.GlobalExceptionHandler;
import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
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
                .andExpect(jsonPath("$.screenCode").value("s500.1"));
    }

    @Test
    void start_ReturnsBadRequest_WhenClientIdIsBlank() throws Exception {
        StartRequest request = StartRequest.builder()
                .clientId("")
                .type(ProcessType.MINOR)
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
                .state(ProcessState.MINOR_OCCUPATION_SCREEN)
                .type(ProcessType.MINOR)
                .build();

        when(flowService.getProcess("123")).thenReturn(instance);

        mockMvc.perform(get("/process/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"))
                .andExpect(jsonPath("$.state").value("MINOR_OCCUPATION_SCREEN"));
    }

    @Test
    void get_ReturnsNotFound_WhenProcessDoesNotExist() throws Exception {
        when(flowService.getProcess("nonexistent"))
                .thenThrow(new NoSuchElementException("Process not found"));

        mockMvc.perform(get("/process/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Process not found"));
    }

    @Test
    void event_ReturnsUpdatedInstance_WhenSubmittingOccupation() throws Exception {
        Map<String, Object> data = Map.of(
                "occupation", "Student",
                "school", "High School"
        );

        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.SUBMIT_OCCUPATION)
                .data(data)
                .build();

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.INCOME_SCREEN)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.SUBMIT_OCCUPATION), eq(data)))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INCOME_SCREEN"));
    }

    @Test
    void event_ReturnsUpdatedInstance_WhenSubmittingIncome() throws Exception {
        Map<String, Object> data = Map.of(
                "income", 5000,
                "toContinue", true
        );

        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.SUBMIT_INCOME)
                .data(data)
                .build();

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.EXPENSES_SCREEN)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.SUBMIT_INCOME), eq(data)))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EXPENSES_SCREEN"));
    }

    @Test
    void event_ReturnsUpdatedInstance_WhenPerformingDocumentMatch() throws Exception {
        Map<String, Object> data = Map.of(
                "scanMatch", "OK",
                "confidence", 0.95
        );

        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.PERFORM_DOCUMENT_MATCH)
                .data(data)
                .build();

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.FACE_RECOGNITION_UPLOAD)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.PERFORM_DOCUMENT_MATCH), eq(data)))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FACE_RECOGNITION_UPLOAD"));
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
    void event_ReturnsNotFound_WhenProcessIdDoesNotExist() throws Exception {
        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.SUBMIT_OCCUPATION)
                .data(Map.of("occupation", "Student"))
                .build();

        when(flowService.handleEvent(eq("nonexistent"), any(), any()))
                .thenThrow(new NoSuchElementException("Process not found"));

        mockMvc.perform(post("/process/nonexistent/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Process not found"));
    }

    @Test
    void asyncResult_ReturnsUpdatedInstance_WhenDocumentMatchCompleted() throws Exception {
        AsyncResultRequest request = new AsyncResultRequest();
        request.setType("document_match");
        request.setResult(Map.of(
                "scanMatch", "OK",
                "confidence", 0.95,
                "verificationId", "doc123"
        ));

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.FACE_RECOGNITION_UPLOAD)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.PERFORM_DOCUMENT_MATCH), any()))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/async-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FACE_RECOGNITION_UPLOAD"));
    }

    @Test
    void asyncResult_ReturnsUpdatedInstance_WhenFaceRecognitionCompleted() throws Exception {
        AsyncResultRequest request = new AsyncResultRequest();
        request.setType("face_recognition");
        request.setResult(Map.of(
                "status", "SUCCESS",
                "faceId", "face123",
                "matchScore", "0.95",
                "livenessScore", "0.98"
        ));

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.CUSTOMER_INFO_VALIDATION)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.UPLOAD_FACE_RECOGNITION), any()))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/async-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CUSTOMER_INFO_VALIDATION"));
    }

    @Test
    void asyncResult_ReturnsUpdatedInstance_WhenCustomerValidationCompleted() throws Exception {
        AsyncResultRequest request = new AsyncResultRequest();
        request.setType("customer_validation");
        request.setResult(Map.of(
                "oneToManyStatus", "OK",
                "validationId", "val123",
                "riskLevel", "LOW"
        ));

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.SIGNATURE_EXAMPLE_SCREEN)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.VALIDATE_CUSTOMER_INFO), any()))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/async-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SIGNATURE_EXAMPLE_SCREEN"));
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
        request.setType("document_match");
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

    @Test
    void advance_ReturnsUpdatedInstance_WhenValid() throws Exception {
        Map<String, Object> data = Map.of(
                "toContinue", true,
                "income", 5000
        );

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.INCOME_SCREEN)
                .build();

        when(flowService.advance(eq("123"), eq(data)))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("data", data))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INCOME_SCREEN"));
    }

    @Test
    void advance_ReturnsNotFound_WhenProcessDoesNotExist() throws Exception {
        when(flowService.advance(eq("nonexistent"), any()))
                .thenThrow(new NoSuchElementException("Process not found"));

        mockMvc.perform(post("/process/nonexistent/advance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Process not found"));
    }
}
