package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class WelcomeCompleteInfoPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.WELCOME
                && event == ProcessEvent.COMPLETE_WELCOME;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        // Final screen is intentionally permissive. No strict validation by default.
        log.debug("[Precondition] {} validating. processId={}, state={}, payloadKeys={}", getClass().getSimpleName(), pi.getId(), pi.getState(), payload != null ? payload.keySet() : "{}");
        List<PreconditionError> errors = new ArrayList<>();
        log.debug("[Precondition] {} completed. errorsCount={}", getClass().getSimpleName(), errors.size());
        return errors;
    }
}
