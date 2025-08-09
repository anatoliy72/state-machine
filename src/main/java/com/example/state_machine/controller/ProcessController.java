package com.example.state_machine.controller;

import com.example.state_machine.controller.dto.AsyncResultRequest;
import com.example.state_machine.controller.dto.EventRequest;
import com.example.state_machine.controller.dto.StartConversionRequest;
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

/**
 * REST controller for managing process instances and triggering state machine events.
 */
@Slf4j
@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
@Validated
public class ProcessController {

    private final FlowService flowService;

    /**
     * Starts a new process instance for the given client and process type.
     *
     * @param request the start request containing clientId, process type, and optional initial data.
     * @return the created {@link ProcessInstance} with initial state.
     */
    @PostMapping("/start")
    public ResponseEntity<ProcessInstance> start(@Valid @RequestBody StartRequest request) {
        ProcessInstance instance = flowService.startProcess(
                request.getClientId(),
                request.getType(),
                request.getInitialData() != null ? request.getInitialData() : Map.of()
        );
        return ResponseEntity.ok(instance);
    }

    /**
     * Retrieves a process instance by its ID.
     *
     * @param id the process instance ID.
     * @return the {@link ProcessInstance} with current state and variables.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProcessInstance> get(@PathVariable String id) {
        return ResponseEntity.ok(flowService.getProcess(id));
    }

    /**
     * Sends an event to the process instance's state machine.
     * This will attempt to trigger a state transition based on the current state and event.
     *
     * @param id      the process instance ID.
     * @param request the {@link EventRequest} containing the event and optional data to attach.
     * @return the updated {@link ProcessInstance} after processing the event.
     */
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

    /**
     * Processes asynchronous results (e.g., KYC verification or biometry) and maps them to process events.
     *
     * @param id      the process instance ID.
     * @param request the {@link AsyncResultRequest} containing async result type and result payload.
     * @return the updated {@link ProcessInstance} after handling the async result.
     */
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

    /**
     * Maps an asynchronous result type string to the corresponding {@link ProcessEvent}.
     *
     * @param type the async result type (e.g., "kyc", "biometry").
     * @return the mapped {@link ProcessEvent}.
     * @throws IllegalArgumentException if the type is null, blank, or unknown.
     */
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

    /**
     * Starts a new process instance for converting a MINOR account into a REGULAR account.
     * This starts the process directly in the {@link com.example.state_machine.model.ProcessState#MINOR_ACCOUNT_IDENTIFIED} state.
     *
     * @param req the {@link StartConversionRequest} containing clientId, minor account ID, and optional initial data.
     * @return the created {@link ProcessInstance} for conversion.
     */
    @PostMapping("/conversion/start")
    public ResponseEntity<ProcessInstance> startMinorToRegular(@Valid @RequestBody StartConversionRequest req) {
        ProcessInstance instance = flowService.startMinorToRegularConversion(
                req.getClientId(),
                req.getMinorAccountId(),
                req.getInitialData()
        );
        return ResponseEntity.ok(instance);
    }
}
