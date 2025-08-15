package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class InformationActivitiesRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.INFORMATION_ACTIVITIES_SCREEN
                && event == ProcessEvent.SUBMIT_INFORMATION_ACTIVITIES;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        Object prefs = read(payload, pi, "communicationPreferences");
        if (prefs == null ||
            (prefs instanceof String s && s.isBlank()) ||
            (prefs instanceof Collection<?> c && c.isEmpty())) {
            errors.add(new PreconditionError("communicationPreferences", "REQUIRED"));
        }
        return errors;
    }
}
