package com.example.state_machine.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartConversionRequest {

    @NotBlank
    private String clientId;

    /**
     * Identifier of the linked MINOR account.
     * Can be stored in the process variables for traceability or auditing.
     */
    private String minorAccountId;

    /**
     * Optional additional data that will be placed into the process variables (mutable).
     */
    private Map<String, Object> initialData;
}
