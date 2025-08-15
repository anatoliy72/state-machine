package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class WelcomeCompleteInfoPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.WELCOME
                && event == ProcessEvent.COMPLETE_WELCOME;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        // Обычно финальный шаг можно не валидировать строго; оставлено пустым.
        // Если хочешь — раскомментируй требование welcomeMessage:
        /*
        List<PreconditionError> errors = new ArrayList<>();
        if (isEmpty(read(payload, pi, "welcomeMessage"))) {
            errors.add(new PreconditionError("welcomeMessage", "REQUIRED"));
        }
        return errors;
        */
        return new ArrayList<>();
    }
}
