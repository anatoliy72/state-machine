package com.example.state_machine.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "process_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstance {
    @Id
    private String id;

    @NotBlank
    private String clientId;

    @NotNull
    private ProcessType type;

    @NotNull
    private ProcessState state;

    @Builder.Default
    private Map<String, Object> variables = Map.of();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
