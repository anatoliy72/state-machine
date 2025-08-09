package com.example.state_machine.controller.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class VariablesUpdateRequest {
    @NotNull
    private Map<String, Object> updates;
}
