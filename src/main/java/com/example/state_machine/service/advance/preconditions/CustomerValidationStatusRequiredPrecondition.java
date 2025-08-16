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
public class CustomerValidationStatusRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.CUSTOMER_INFO_VALIDATION
                && event == ProcessEvent.VALIDATE_CUSTOMER_INFO;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        log.debug("[Precondition] {} validating. processId={}, state={}, payloadKeys={}", getClass().getSimpleName(), pi.getId(), pi.getState(), payload != null ? payload.keySet() : "{}");
        List<PreconditionError> errors = new ArrayList<>();
        Object status = read(payload, pi, "oneToManyStatus");
        log.debug("[Precondition] oneToManyStatus={}", status);
        if (isEmpty(status)) {
            errors.add(new PreconditionError("oneToManyStatus", "REQUIRED"));
        } else if (!in(status, "OK", "FAIL")) {
            errors.add(new PreconditionError("oneToManyStatus", "MUST_BE_OK_OR_FAIL"));
        }
        log.debug("[Precondition] {} completed. errorsCount={}", getClass().getSimpleName(), errors.size());
        return errors;
    }
}
