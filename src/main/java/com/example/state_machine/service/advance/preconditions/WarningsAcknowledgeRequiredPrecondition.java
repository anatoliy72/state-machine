package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class WarningsAcknowledgeRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.WARNINGS
                && event == ProcessEvent.ACKNOWLEDGE_WARNINGS;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        Object ack = read(payload, pi, "warningsAcknowledged");
        if (ack == null ||
            (ack instanceof String s && s.isBlank()) ||
            (ack instanceof Collection<?> c && c.isEmpty())) {
            errors.add(new PreconditionError("warningsAcknowledged", "REQUIRED"));
        }
        return errors;
    }
}
