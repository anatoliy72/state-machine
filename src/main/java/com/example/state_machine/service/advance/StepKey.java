package com.example.state_machine.service.advance;

import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;

public record StepKey(ProcessType type, ProcessState state) { }
