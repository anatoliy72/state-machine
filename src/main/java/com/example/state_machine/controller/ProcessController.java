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

        // Извлекаем событие из запроса или используем событие по умолчанию для состояния
        ProcessEvent event = request.getEvent() != null ?
                ProcessEvent.valueOf(request.getEvent()) :
                getDefaultEventForState(String.valueOf(flowService.getProcess(id).getState()));

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
        return switch (type.toLowerCase()) {
            case "document_match" -> ProcessEvent.PERFORM_DOCUMENT_MATCH;
            case "face_recognition" -> ProcessEvent.UPLOAD_FACE_RECOGNITION;
            case "customer_validation" -> ProcessEvent.VALIDATE_CUSTOMER_INFO;
            default -> throw new IllegalArgumentException("Unknown async result type: " + type);
        };
    }

    private ProcessEvent getDefaultEventForState(String currentState) {
        return switch (currentState) {
            case "STARTED" -> ProcessEvent.START_FLOW;
            case "INCOME_SCREEN" -> ProcessEvent.SUBMIT_INCOME;
            case "PERFORM_MATCH" -> ProcessEvent.PERFORM_DOCUMENT_MATCH;
            case "MINOR_OCCUPATION_SCREEN" -> ProcessEvent.SUBMIT_OCCUPATION;
            case "EXPENSES_SCREEN" -> ProcessEvent.SUBMIT_EXPENSES;
            case "GENERATE_SCAN" -> ProcessEvent.GENERATE_DOCUMENT_SCAN;
            case "SPEECH_TO_TEXT" -> ProcessEvent.PROCESS_SPEECH_TO_TEXT;
            case "FACE_RECOGNITION_UPLOAD" -> ProcessEvent.UPLOAD_FACE_RECOGNITION;
            case "CUSTOMER_INFO_VALIDATION" -> ProcessEvent.VALIDATE_CUSTOMER_INFO;
            case "SIGNATURE_EXAMPLE_SCREEN" -> ProcessEvent.SUBMIT_SIGNATURE;
            case "ACCOUNT_ACTIVITIES_SCREEN" -> ProcessEvent.SUBMIT_ACCOUNT_ACTIVITIES;
            case "STUDENT_PACKAGES_SCREEN" -> ProcessEvent.SUBMIT_STUDENT_PACKAGES;
            case "VIDEO_SCREEN" -> ProcessEvent.SUBMIT_VIDEO;
            case "CUSTOMER_ADDRESS_SCREEN" -> ProcessEvent.SUBMIT_ADDRESS;
            case "CHOOSE_BRANCH_SCREEN" -> ProcessEvent.SUBMIT_BRANCH_CHOICE;
            case "INFORMATION_ACTIVITIES_SCREEN" -> ProcessEvent.SUBMIT_INFORMATION_ACTIVITIES;
            case "TWO_MORE_QUESTIONS_SCREEN" -> ProcessEvent.SUBMIT_ADDITIONAL_QUESTIONS;
            case "SERVICE_SUBSCRIPTION", "NO_SERVICE_SUBSCRIPTION" -> ProcessEvent.SUBMIT_FORMS;
            case "FORMS" -> ProcessEvent.ACKNOWLEDGE_WARNINGS;
            case "WARNINGS" -> ProcessEvent.COMPLETE_WELCOME;
            default -> throw new IllegalStateException("No default event for state: " + currentState);
        };
    }
}
