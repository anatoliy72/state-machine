package com.example.state_machine.controller.dto;

import com.example.state_machine.model.ProcessEvent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class EventRequest {
    @NotNull
    private ProcessEvent event;
    private Map<String, Object> data;
}
