package com.example.state_machine.config;

import com.example.state_machine.controller.dto.*;
import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(
        name = "Process API",
        description = "Manage account opening workflow instances: start, fetch, update variables, " +
                "send events, process async results, server-driven advance, and minor→regular conversion."
)
@RequestMapping("/process")
public interface ProcessApi {

    // ---- START ----
    @Operation(
            summary = "Start a new process",
            description = "Creates a new process instance in STARTED state for the provided client and process type.",
            requestBody = @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = StartRequest.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Process created",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
            }
    )
    @PostMapping("/start")
    ResponseEntity<ProcessInstanceDto> start(@Valid @org.springframework.web.bind.annotation.RequestBody StartRequest request);

    // ---- GET PROCESS ----
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
    ResponseEntity<ProcessInstanceDto> get(
            @Parameter(description = "Process ID", required = true) @PathVariable String id);

    // ---- HISTORY ----
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
    ResponseEntity<List<ProcessHistory>> history(
            @Parameter(description = "Process ID", required = true) @PathVariable String id);

    // ---- PATCH VARIABLES ----
    @Operation(
            summary = "Patch process variables",
            description = "Merges provided key-value pairs into process variables without changing state.",
            requestBody = @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = Map.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Variables updated",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content)
            }
    )
    @PatchMapping("/{id}/variables")
    ResponseEntity<ProcessInstanceDto> patchVariables(
            @Parameter(description = "Process ID", required = true) @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> updates);

    // ---- RAW EVENT ----
    @Operation(
            summary = "Send raw event",
            description = "Client-driven event that triggers a transition if allowed for the current state. " +
                    "Server validates preconditions (if any) before applying.",
            requestBody = @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = EventRequest.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Event accepted",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid event payload", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Preconditions not met", content = @Content)
            }
    )
    @PostMapping("/{id}/event")
    ResponseEntity<ProcessInstanceDto> event(
            @Parameter(description = "Process ID", required = true) @PathVariable String id,
            @Valid @org.springframework.web.bind.annotation.RequestBody EventRequest request);

    // ---- ASYNC RESULT ----
    @Operation(
            summary = "Submit async result",
            description = "Accepts results from async services (e.g., KYC, biometry), maps to internal events, " +
                    "and applies transition if valid.",
            requestBody = @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AsyncResultRequest.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Result applied",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "400", description = "Unknown type or invalid payload", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Preconditions not met", content = @Content)
            }
    )
    @PostMapping("/{id}/async-result")
    ResponseEntity<ProcessInstanceDto> asyncResult(
            @Parameter(description = "Process ID", required = true) @PathVariable String id,
            @Valid @org.springframework.web.bind.annotation.RequestBody AsyncResultRequest request);

    // ---- START CONVERSION ----
    @Operation(
            summary = "Start minor→regular conversion",
            description = "Creates a conversion process starting at MINOR_ACCOUNT_IDENTIFIED and prepares SM context.",
            requestBody = @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = StartConversionRequest.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Conversion process created",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
            }
    )
    @PostMapping("/conversion/start")
    ResponseEntity<ProcessInstanceDto> startMinorToRegular(
            @Valid @org.springframework.web.bind.annotation.RequestBody StartConversionRequest req);

    // ---- ADVANCE ----
    @Operation(
            summary = "Advance to next step (server-driven)",
            description = "Resolves the next event based on (type, currentState) using StepPlan, " +
                    "validates preconditions and applies the transition.",
            requestBody = @RequestBody(required = false,
                    content = @Content(schema = @Schema(implementation = AdvanceRequest.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Advanced successfully",
                            content = @Content(schema = @Schema(implementation = ProcessInstanceDto.class))),
                    @ApiResponse(responseCode = "404", description = "Process not found", content = @Content),
                    @ApiResponse(responseCode = "409", description = "Preconditions not met", content = @Content)
            }
    )
    @PostMapping("/{id}/advance")
    ResponseEntity<ProcessInstanceDto> advance(
            @Parameter(description = "Process ID", required = true) @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody(required = false) AdvanceRequest req);

    // -------- helper doc only (no mapping) ----------
    /**
     * Doc helper only: the controller implementation maps async result type (e.g. "kyc", "biometry")
     * to internal {@link ProcessEvent}.
     */
    // ProcessEvent mapAsyncResultTypeToEvent(String type);
}
