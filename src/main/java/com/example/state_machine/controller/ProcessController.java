package com.example.state_machine.controller;

import com.example.state_machine.controller.dto.AsyncResultRequest;
import com.example.state_machine.controller.dto.EventRequest;
import com.example.state_machine.controller.dto.StartRequest;
import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.service.FlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
@Validated
public class ProcessController {

    private final FlowService flowService;

    @PostMapping("/start")
    public ResponseEntity<ProcessInstance> start(@Valid @RequestBody StartRequest request) {
        ProcessInstance instance = flowService.startProcess(
                request.getClientId(),
                request.getType(),
                request.getInitialData() != null ? request.getInitialData() : Map.of()
        );
        return ResponseEntity.ok(instance);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessInstance> get(@PathVariable String id) {
        return ResponseEntity.ok(flowService.getProcess(id));
    }

    @PostMapping("/{id}/event")
    public ResponseEntity<ProcessInstance> event(
            @PathVariable String id,
            @Valid @RequestBody EventRequest request) {
        if (request.getEvent() == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
        return ResponseEntity.ok(flowService.handleEvent(
                id,
                request.getEvent(),
                request.getData() != null ? request.getData() : Map.of()
        ));
    }

    @PostMapping("/{id}/async-result")
    public ResponseEntity<ProcessInstance> asyncResult(
            @PathVariable String id,
            @Valid @RequestBody AsyncResultRequest request) {
        return ResponseEntity.ok(flowService.handleEvent(
                id,
                mapAsyncResultTypeToEvent(request.getType()),
                request.getResult()
        ));
    }

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
