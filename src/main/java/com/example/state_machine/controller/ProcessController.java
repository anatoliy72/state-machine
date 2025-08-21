package com.example.state_machine.controller;

import com.example.state_machine.config.ProcessApi;
import com.example.state_machine.controller.dto.*;
import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessHistory;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.service.FlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class ProcessController implements ProcessApi {

    private final FlowService flowService;

    @Override
    public ResponseEntity<ProcessInstanceDto> start(@Valid @org.springframework.web.bind.annotation.RequestBody StartRequest request) {
        ProcessInstance instance = flowService.startProcess(
                request.getClientId(),
                request.getType(),
                request.getInitialData() != null ? request.getInitialData() : Map.of()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    @Override
    public ResponseEntity<ProcessInstanceDto> get(String id) {
        ProcessInstance instance = flowService.getProcess(id);
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    @Override
    public ResponseEntity<List<ProcessHistory>> history(String id) {
        return ResponseEntity.ok(flowService.getHistory(id));
    }

    @Override
    public ResponseEntity<ProcessInstanceDto> patchVariables(String id, Map<String, Object> updates) {
        ProcessInstance updated = flowService.updateVariables(id, updates != null ? updates : Map.of());
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(updated));
    }

    @Override
    public ResponseEntity<ProcessInstanceDto> event(String id, @Valid @org.springframework.web.bind.annotation.RequestBody EventRequest request) {
        if (request.getEvent() == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        ProcessInstance instance = flowService.handleEvent(
                id,
                request.getEvent(),
                request.getData() != null ? request.getData() : Map.of()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    @Override
    public ResponseEntity<ProcessInstanceDto> asyncResult(String id, @Valid @org.springframework.web.bind.annotation.RequestBody AsyncResultRequest request) {
        ProcessInstance instance = flowService.handleEvent(
                id,
                mapAsyncResultTypeToEvent(request.getType()),
                request.getResult() != null ? request.getResult() : Map.of()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    @Override
    public ResponseEntity<ProcessInstanceDto> advance(String id, AdvanceRequest request) {
        log.info("Processing advance request for id={}, event={}, data={}", id, request.getEvent(), request.getData());
    // If no explicit event is provided, use server-driven advance() which derives next event from StepPlan
    if (request == null || request.getEvent() == null) {
        ProcessInstance advanced = flowService.advance(
            id,
            request != null && request.getData() != null ? request.getData() : Map.of()
        );
        log.info("Process advanced to state: {}", advanced.getState());
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(advanced));
    }

    // When an explicit event name is provided, convert and send it via advanceProcess
    ProcessEvent event = ProcessEvent.valueOf(request.getEvent());
    log.info("Resolved event: {}", event);
    ProcessInstance advanced = flowService.advanceProcess(
        id,
        event,
        request.getData() != null ? request.getData() : Map.of()
    );
    log.info("Process advanced to state: {}", advanced.getState());
    return ResponseEntity.ok(ProcessInstanceDto.fromEntity(advanced));
    }

    // ---------- helpers ----------
    private ProcessEvent mapAsyncResultTypeToEvent(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Async result type cannot be empty");
        }
        // In the minimal flow we don't support async results; throw for any input
        throw new IllegalArgumentException("Async results are not supported in the trimmed flow: " + type);
    }

    private ProcessEvent getDefaultEventForState(String currentState) {
        return switch (currentState) {
            case "STARTED" -> ProcessEvent.MINOR_OCCUPATION_SCREEN;
            case "MINOR_OCCUPATION_SCREEN" -> ProcessEvent.INCOME_SCREEN;
            case "INCOME_SCREEN" -> ProcessEvent.MINOR_OCCUPATION_SCREEN;
            default -> throw new IllegalStateException("No default event for state: " + currentState);
        };
    }
}
