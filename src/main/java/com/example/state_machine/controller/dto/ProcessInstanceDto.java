package com.example.state_machine.controller.dto;


import com.example.state_machine.model.ProcessState;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessInstanceDto {

    private String id;
    private String clientId;
    private ProcessState state;
    private String screenCode; // <-- added
    private Map<String, Object> variables;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProcessInstanceDto fromEntity(com.example.state_machine.model.ProcessInstance entity) {
        return ProcessInstanceDto.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .state(entity.getState())
                .screenCode(entity.getState().getScreenCode()) // <-- mapping from enum
                .variables(entity.getVariables())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
