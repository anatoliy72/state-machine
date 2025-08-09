package com.example.state_machine.service.advance;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PreconditionRegistry {

    private final List<Precondition> checks; // Spring injects all @Component Precondition beans

    public List<PreconditionError> validateAll(ProcessInstance pi, ProcessEvent e, Map<String, Object> payload) {
        return checks.stream()
                .filter(p -> p.supports(pi.getType(), pi.getState(), e))
                .flatMap(p -> p.validate(pi, payload).stream())
                .toList();
    }
}
