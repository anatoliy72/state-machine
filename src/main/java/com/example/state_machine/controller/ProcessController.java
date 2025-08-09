package com.example.state_machine.controller;

import com.example.state_machine.controller.dto.*;
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

    // --- SERVER-DRIVEN ---
    // Starts a process — server decides initial state and flow
    /**
     * Starts a new process instance for the given client and process type.
     *
     * @param request the start request containing clientId, process type, and optional initial data.
     * @return the created process with initial state and screen code.
     */
    @PostMapping("/start")
    public ResponseEntity<ProcessInstanceDto> start(@Valid @RequestBody StartRequest request) {
        ProcessInstance instance = flowService.startProcess(
                request.getClientId(),
                request.getType(),
                request.getInitialData() != null ? request.getInitialData() : Map.of()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    // --- SERVER-DRIVEN ---
    // Fetches process instance info — no state change
    /**
     * Retrieves a process instance by its ID.
     *
     * @param id the process instance ID.
     * @return the process with current state, screen code, and variables.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProcessInstanceDto> get(@PathVariable String id) {
        ProcessInstance instance = flowService.getProcess(id);
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    // --- RAW (CLIENT-DRIVEN) ---
    // Client explicitly sends an event — full control over state transitions
    /**
     * Sends an event to the process instance's state machine.
     * This will attempt to trigger a state transition based on the current state and event.
     *
     * @param id      the process instance ID.
     * @param request the {@link EventRequest} containing the event and optional data to attach.
     * @return the updated process after processing the event (with state + screen code).
     */
    @PostMapping("/{id}/event")
    public ResponseEntity<ProcessInstanceDto> event(
            @PathVariable String id,
            @Valid @RequestBody EventRequest request) {
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

    // --- RAW (CLIENT-DRIVEN, but mapped by server) ---
    // Client sends async result type (e.g., "kyc"), server maps it to an internal event
    /**
     * Processes asynchronous results (e.g., KYC verification or biometry) and maps them to process events.
     *
     * @param id      the process instance ID.
     * @param request the {@link AsyncResultRequest} containing async result type and result payload.
     * @return the updated process after handling the async result (with state + screen code).
     */
    @PostMapping("/{id}/async-result")
    public ResponseEntity<ProcessInstanceDto> asyncResult(
            @PathVariable String id,
            @Valid @RequestBody AsyncResultRequest request) {
        ProcessInstance instance = flowService.handleEvent(
                id,
                mapAsyncResultTypeToEvent(request.getType()),
                request.getResult()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }


    //--- SERVER-DRIVEN ---
    // Starts a conversion process directly in MINOR_ACCOUNT_IDENTIFIED state
    /**
     * Starts a new process instance for converting a MINOR account into a REGULAR account.
     * This starts the process directly in the {@link com.example.state_machine.model.ProcessState#MINOR_ACCOUNT_IDENTIFIED} state.
     *
     * @param req the {@link StartConversionRequest} containing clientId, minor account ID, and optional initial data.
     * @return the created process for conversion (with state + screen code).
     */
    @PostMapping("/conversion/start")
    public ResponseEntity<ProcessInstanceDto> startMinorToRegular(@Valid @RequestBody StartConversionRequest req) {
        ProcessInstance instance = flowService.startMinorToRegularConversion(
                req.getClientId(),
                req.getMinorAccountId(),
                req.getInitialData()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    // --- SERVER-DRIVEN ---
    // Advances to the next state automatically if server conditions are met
    @PostMapping("/{id}/advance")
    public ResponseEntity<ProcessInstanceDto> advance(
            @PathVariable String id,
            @RequestBody(required = false) AdvanceRequest req) {
        ProcessInstance instance = flowService.advance(
                id,
                (req != null && req.getData() != null) ? req.getData() : Map.of()
        );
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
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

}
