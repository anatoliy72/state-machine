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
    public ResponseEntity<ProcessInstanceDto> startMinorToRegular(@Valid @org.springframework.web.bind.annotation.RequestBody StartConversionRequest req) {
        ProcessInstance instance = flowService.startMinorToRegularConversion(
                req.getClientId(),
                req.getMinorAccountId(),
                req.getInitialData()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    @Override
    public ResponseEntity<ProcessInstanceDto> advance(String id, @org.springframework.web.bind.annotation.RequestBody(required = false) AdvanceRequest req) {
        ProcessInstance instance = flowService.advance(
                id,
                (req != null && req.getData() != null) ? req.getData() : Map.of()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    // ---------- helpers ----------
    private ProcessEvent mapAsyncResultTypeToEvent(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Async result type cannot be empty");
        }
        return switch (type.toLowerCase()) {
            case "kyc" -> ProcessEvent.KYC_VERIFIED;
            case "biometry" -> ProcessEvent.BIOMETRY_SUCCESS;
            default -> throw new IllegalArgumentException("Unknown async result type: " + type);
        };
    }
}
