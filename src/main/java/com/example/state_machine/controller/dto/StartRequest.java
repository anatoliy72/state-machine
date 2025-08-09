package com.example.state_machine.controller.dto;

import com.example.state_machine.model.ProcessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class StartRequest {
    @NotBlank
    private String clientId;
    @NotNull
    private ProcessType type;
    private Map<String, Object> initialData;
}
