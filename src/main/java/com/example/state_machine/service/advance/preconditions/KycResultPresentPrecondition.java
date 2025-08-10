package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class KycResultPresentPrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        // Only check when we try to fire KYC_VERIFIED
        return event == ProcessEvent.KYC_VERIFIED;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        // Example logic:
        // 1) Prefer DB/external check (not shown). Here we fallback to variables.
        // 2) Accept if either incoming payload or existing variables contains approved KYC.

        List<PreconditionError> errors = new ArrayList<>();

        Object status = payload.getOrDefault("status",
                pi.getVariables() != null ? pi.getVariables().get("status") : null);

        if (status == null) {
            errors.add(new PreconditionError("KYC_RESULT_PRESENT", "KYC result not found"));
            return errors;
        }

        if (!"APPROVED".equalsIgnoreCase(String.valueOf(status))) {
            errors.add(new PreconditionError("KYC_NOT_APPROVED", "KYC status must be APPROVED"));
        }

        return errors;
    }
}
