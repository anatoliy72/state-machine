package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ParentConsentPresentPrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR && event == ProcessEvent.PARENT_APPROVED;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        Object consentDoc = firstNotNull(
                payload.get("consentDocument"),
                pi.getVariables() != null ? pi.getVariables().get("consentDocument") : null
        );
        if (consentDoc == null) {
            errors.add(new PreconditionError("CONSENT_DOCUMENT_REQUIRED", "Parent consent document is required"));
        }
        return errors;
    }

    private Object firstNotNull(Object a, Object b) {
        return a != null ? a : b;
    }
}
