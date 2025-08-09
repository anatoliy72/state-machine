package com.example.state_machine.controller.dto;

import com.example.state_machine.model.ProcessEvent;
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
public class EventRequest {
    @NotNull
    private ProcessEvent event;
    private Map<String, Object> data;
}
