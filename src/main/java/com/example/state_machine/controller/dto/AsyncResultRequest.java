package com.example.state_machine.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class AsyncResultRequest {
    @NotBlank
    private String type;
    @NotNull
    private Map<String, Object> result;
}
