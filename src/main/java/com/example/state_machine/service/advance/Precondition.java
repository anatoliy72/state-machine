package com.example.state_machine.service.advance;

import com.example.state_machine.model.*;

import java.util.List;
import java.util.Map;

public interface Precondition {
    boolean supports(ProcessType type, ProcessState state, ProcessEvent event);
    List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload);
}
