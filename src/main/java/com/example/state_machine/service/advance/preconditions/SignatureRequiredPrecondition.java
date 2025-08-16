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
public class SignatureRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.SIGNATURE_EXAMPLE_SCREEN
                && event == ProcessEvent.SUBMIT_SIGNATURE;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        log.debug("[Precondition] {} validating. processId={}, state={}, payloadKeys={}", getClass().getSimpleName(), pi.getId(), pi.getState(), payload != null ? payload.keySet() : "{}");
        List<PreconditionError> errors = new ArrayList<>();
        Object image = read(payload, pi, "signatureImage");
        log.debug("[Precondition] signatureImage present={}", image != null);
        if (isEmpty(image)) {
            errors.add(new PreconditionError("signatureImage", "REQUIRED"));
        }
        log.debug("[Precondition] {} completed. errorsCount={}", getClass().getSimpleName(), errors.size());
        return errors;
    }
}
