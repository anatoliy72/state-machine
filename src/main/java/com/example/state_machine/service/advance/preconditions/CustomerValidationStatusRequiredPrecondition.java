package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CustomerValidationStatusRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.CUSTOMER_INFO_VALIDATION
                && event == ProcessEvent.VALIDATE_CUSTOMER_INFO;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        Object status = read(payload, pi, "oneToManyStatus");
        if (isEmpty(status)) {
            errors.add(new PreconditionError("oneToManyStatus", "REQUIRED"));
        } else if (!in(status, "OK", "FAIL")) {
            errors.add(new PreconditionError("oneToManyStatus", "MUST_BE_OK_OR_FAIL"));
        }
        return errors;
    }
}
