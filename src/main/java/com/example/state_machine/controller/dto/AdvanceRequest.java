package com.example.state_machine.controller.dto;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdvanceRequest {
    /**
     * Optional payload client provides on this step.
     * Will be merged into process variables if the step succeeds.
     */
    private Map<String, Object> data;
}
