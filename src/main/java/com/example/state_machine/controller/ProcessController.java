package com.example.state_machine.controller;

import com.example.state_machine.controller.dto.AsyncResultRequest;
import com.example.state_machine.controller.dto.EventRequest;
import com.example.state_machine.controller.dto.StartRequest;
import com.example.state_machine.controller.dto.VariablesUpdateRequest;
import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.service.FlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
@Validated
public class ProcessController {
    private final FlowService flowService;

    @PostMapping("/start")
    public ResponseEntity<ProcessInstance> start(@Valid @RequestBody StartRequest request) {
        ProcessInstance instance = flowService.startProcess(request.getClientId(), request.getType(), request.getInitialData());
        return ResponseEntity.ok(instance);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessInstance> get(@PathVariable String id) {
        return ResponseEntity.ok(flowService.getProcess(id));
    }

    @PostMapping("/{id}/event")
    public ResponseEntity<ProcessInstance> event(@PathVariable String id, @Valid @RequestBody EventRequest request) {
        ProcessInstance instance = flowService.handleEvent(id, request.getEvent(), request.getData());
        return ResponseEntity.ok(instance);
    }

    @PostMapping("/{id}/async-result")
    public ResponseEntity<ProcessInstance> asyncResult(@PathVariable String id, @Valid @RequestBody AsyncResultRequest request) {
        ProcessEvent event = mapAsyncResultTypeToEvent(request.getType());
        ProcessInstance instance = flowService.handleEvent(id, event, request.getResult());
        return ResponseEntity.ok(instance);
    }

    @PatchMapping("/{id}/variables")
    public ResponseEntity<ProcessInstance> updateVariables(@PathVariable String id, @Valid @RequestBody VariablesUpdateRequest request) {
        ProcessInstance instance = flowService.updateVariables(id, request.getUpdates());
        return ResponseEntity.ok(instance);
    }

    private ProcessEvent mapAsyncResultTypeToEvent(String type) {
        if ("BIOMETRY/SUCCESS".equalsIgnoreCase(type)) {
            return ProcessEvent.BIOMETRY_SUCCESS;
        }
        throw new IllegalArgumentException("Unknown async result type: " + type);
    }
}
