package com.example.state_machine.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * MongoDB document representing a single audit record of a process state transition.
 * <p>
 * Each record captures:
 * <ul>
 *   <li>Which process was affected ({@code processId})</li>
 *   <li>The state transition ({@code fromState} → {@code toState})</li>
 *   <li>The triggering event ({@code event}), if any</li>
 *   <li>The timestamp when the record was created ({@code timestamp})</li>
 *   <li>The event payload received from the client ({@code payload})</li>
 *   <li>A snapshot of the process variables after the transition ({@code variablesSnapshot})</li>
 * </ul>
 * For the initial snapshot, {@code fromState} and {@code event} may be {@code null}.
 */
@Document(collection = "process_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessHistory {

    @Id
    private String id;

    private String processId;

    /**
     * Previous state. May be {@code null} for the initial snapshot.
     */
    private ProcessState fromState;

    private ProcessState toState;

    /**
     * Triggering event. May be {@code null} for the initial snapshot.
     */
    private ProcessEvent event;

    /**
     * Timestamp when this history record was created.
     */
    private Instant timestamp;

    /**
     * Event payload as received in the request (i.e., {@code data}).
     */
    private Map<String, Object> payload;

    /**
     * Snapshot of process {@code variables} after the transition.
     */
    private Map<String, Object> variablesSnapshot;
}
