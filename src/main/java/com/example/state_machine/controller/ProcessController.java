package com.example.state_machine.controller;

import com.example.state_machine.controller.dto.AdvanceRequest;
import com.example.state_machine.controller.dto.AsyncResultRequest;
import com.example.state_machine.controller.dto.EventRequest;
import com.example.state_machine.controller.dto.ProcessInstanceDto;
import com.example.state_machine.controller.dto.StartConversionRequest;
import com.example.state_machine.controller.dto.StartRequest;
import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessHistory;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.service.FlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing process instances and triggering state machine events.
 */
@Slf4j
@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "Process API",
        description = "Manage account opening workflow instances: start, fetch, update variables, " +
                "send events, process async results, server-driven advance, and minor→regular conversion."
)
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
    @Operation(
            summary = "Start a new process",
            description = "Creates a new process instance in STARTED state for the provided client and process type.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = StartRequest.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Process created",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
            }
    )
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
    @Operation(
            summary = "Get process by ID",
            description = "Returns current state, screen code and variables for the specified process instance.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Process found",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content)
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<ProcessInstanceDto> get(
            @Parameter(description = "Process ID", required = true) @PathVariable String id) {
        ProcessInstance instance = flowService.getProcess(id);
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(instance));
    }

    // --- HISTORY ---

    /**
     * Returns full transition history for the process.
     */
    @Operation(
            summary = "Get process history",
            description = "Returns chronological transition history with payloads and variable snapshots.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "History returned",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProcessHistory.class)))),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content)
            }
    )
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ProcessHistory>> history(
            @Parameter(description = "Process ID", required = true) @PathVariable String id) {
        return ResponseEntity.ok(flowService.getHistory(id));
    }

    // --- VARIABLES (async updates) ---

    /**
     * Partially updates process variables (e.g., async results like voiceScore).
     */
    @Operation(
            summary = "Patch process variables",
            description = "Merges provided key-value pairs into process variables without changing state.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Variables updated",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content)
            }
    )
    @PatchMapping("/{id}/variables")
    public ResponseEntity<ProcessInstanceDto> patchVariables(
            @Parameter(description = "Process ID", required = true) @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        ProcessInstance updated = flowService.updateVariables(id, updates != null ? updates : Map.of());
        return ResponseEntity.ok(ProcessInstanceDto.fromEntity(updated));
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
    @Operation(
            summary = "Send raw event",
            description = "Client-driven event that triggers a transition if allowed for the current state. " +
                    "Server validates preconditions (if any) before applying.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = EventRequest.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Event accepted",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid event payload", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Preconditions not met", content = @Content)
            }
    )
    @PostMapping("/{id}/event")
    public ResponseEntity<ProcessInstanceDto> event(
            @Parameter(description = "Process ID", required = true) @PathVariable String id,
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
    @Operation(
            summary = "Submit async result",
            description = "Accepts results from async services (e.g., KYC, biometry), maps to internal events, " +
                    "and applies transition if valid.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = AsyncResultRequest.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Result applied",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "400", description = "Unknown type or invalid payload", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Preconditions not met", content = @Content)
            }
    )
    @PostMapping("/{id}/async-result")
    public ResponseEntity<ProcessInstanceDto> asyncResult(
            @Parameter(description = "Process ID", required = true) @PathVariable String id,
            @Valid @RequestBody AsyncResultRequest request) {
        ProcessInstance instance = flowService.handleEvent(
                id,
                mapAsyncResultTypeToEvent(request.getType()),
                request.getResult() != null ? request.getResult() : Map.of()
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
    @Operation(
            summary = "Start minor→regular conversion",
            description = "Creates a conversion process starting at MINOR_ACCOUNT_IDENTIFIED and prepares SM context.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = StartConversionRequest.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Conversion process created",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
            }
    )
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

    @Operation(
            summary = "Advance to next step (server-driven)",
            description = "Resolves the next event based on (type, currentState) using StepPlan, " +
                    "validates preconditions and applies the transition.",
            requestBody = @RequestBody(
                    required = false,
                    content = @Content(schema = @Schema(implementation = AdvanceRequest.class))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Advanced successfully",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Preconditions not met", content = @Content)
            }
    )
    @PostMapping("/{id}/advance")
    public ResponseEntity<ProcessInstanceDto> advance(
            @Parameter(description = "Process ID", required = true) @PathVariable String id,
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
// Note: This controller is designed to handle both server-driven and client-driven flows.
