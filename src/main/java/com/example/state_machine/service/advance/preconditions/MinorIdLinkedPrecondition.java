// src/main/java/com/example/state_machine/service/advance/preconditions/MinorIdLinkedPrecondition.java
package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MinorIdLinkedPrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR_TO_REGULAR &&
               (event == ProcessEvent.CONFIRM_CONVERSION || event == ProcessEvent.COMPLETE_CONVERSION);
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        Object linked = pi.getVariables() != null ? pi.getVariables().get("linkedMinorAccountId") : null;
        if (linked == null) {
            errors.add(new PreconditionError("MINOR_ACCOUNT_LINK_REQUIRED", "Linked MINOR account id is required"));
        }
        return errors;
    }
}
