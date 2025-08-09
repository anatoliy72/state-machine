package com.example.state_machine.service.advance;

import com.example.state_machine.model.ProcessState;
import lombok.Getter;

import java.util.List;

@Getter
public class PreconditionsNotMetException extends RuntimeException {
    private final ProcessState state;
    private final List<PreconditionError> errors;

    public PreconditionsNotMetException(ProcessState state, List<PreconditionError> errors) {
        super("Preconditions not met");
        this.state = state;
        this.errors = errors;
    }
}
