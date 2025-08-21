package com.example.state_machine.controller;

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

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        StartRequest request = StartRequest.builder()
                .clientId("minor123")
                .type(ProcessType.MINOR)
                .initialData(Map.of())
                .build();

        ProcessInstance expectedInstance = ProcessInstance.builder()
                .id("2")
                .state(ProcessState.STARTED)
                .type(ProcessType.MINOR)
                .build();

        when(flowService.startProcess(eq("minor123"), eq(ProcessType.MINOR), eq(Map.of())))
                .thenReturn(expectedInstance);

        mockMvc.perform(post("/process/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STARTED"));
    }

    @Test
    void event_ReturnsUpdatedInstance_WhenIncomeEvent() throws Exception {
        Map<String, Object> data = Map.of("income", 1000);

        EventRequest request = EventRequest.builder()
                .event(ProcessEvent.INCOME_SCREEN)
                .data(data)
                .build();

        ProcessInstance updatedInstance = ProcessInstance.builder()
                .id("123")
                .state(ProcessState.INCOME_SCREEN)
                .build();

        when(flowService.handleEvent(eq("123"), eq(ProcessEvent.INCOME_SCREEN), eq(data)))
                .thenReturn(updatedInstance);

        mockMvc.perform(post("/process/123/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INCOME_SCREEN"));
    }

    @Test
    void advance_ReturnsUpdatedInstance_WhenValid() throws Exception {
        Map<String, Object> data = Map.of("income", 5000);

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
}
 
