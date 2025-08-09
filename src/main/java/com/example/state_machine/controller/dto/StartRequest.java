package com.example.state_machine.controller.dto;

import com.example.state_machine.model.ProcessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StartRequest {
    @NotBlank
    private String clientId;
    @NotNull
    private ProcessType type;
    private Map<String, Object> initialData;
}
