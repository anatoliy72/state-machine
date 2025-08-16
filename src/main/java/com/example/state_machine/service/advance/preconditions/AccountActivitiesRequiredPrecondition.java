package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class AccountActivitiesRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.ACCOUNT_ACTIVITIES_SCREEN
                && event == ProcessEvent.SUBMIT_ACCOUNT_ACTIVITIES;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        log.debug("[Precondition] {} validating. processId={}, state={}, payloadKeys={}",
                getClass().getSimpleName(), pi.getId(), pi.getState(), payload != null ? payload.keySet() : "{}");
        List<PreconditionError> errors = new ArrayList<>();
        Object activities = read(payload, pi, "activities");
        if (isEmpty(activities)) {
            activities = read(payload, pi, "selectedActivities");
        }
        boolean ok = !(activities == null ||
            (activities instanceof String s && s.isBlank()) ||
            (activities instanceof Collection<?> c && c.isEmpty()));
        log.debug("[Precondition] activities resolved = {}, valid={}", activities, ok);
        if (!ok) {
            errors.add(new PreconditionError("activities", "REQUIRED"));
        }
        log.debug("[Precondition] {} completed. errorsCount={}", getClass().getSimpleName(), errors.size());
        return errors;
    }
}
