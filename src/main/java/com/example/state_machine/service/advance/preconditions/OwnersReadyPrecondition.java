// src/main/java/com/example/state_machine/service/advance/preconditions/OwnersReadyPrecondition.java
package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OwnersReadyPrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MULTI_OWNER && event == ProcessEvent.CONFIRM_ALL_OWNERS;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();

        Object totalOwners = firstNotNull(
                payload.get("totalOwners"),
                pi.getVariables() != null ? pi.getVariables().get("totalOwners") : null
        );
        Object totalShare = firstNotNull(
                payload.get("totalShare"),
                pi.getVariables() != null ? pi.getVariables().get("totalShare") : null
        );

        if (totalOwners == null) {
            errors.add(new PreconditionError("OWNERS_COUNT_REQUIRED", "Total owners is required"));
        }
        if (totalShare == null) {
            errors.add(new PreconditionError("OWNERS_SHARE_REQUIRED", "Total share is required"));
        }

        try {
            if (totalShare != null && Integer.parseInt(String.valueOf(totalShare)) != 100) {
                errors.add(new PreconditionError("INVALID_TOTAL_SHARE", "Total share must be 100"));
            }
        } catch (NumberFormatException ignored) {
            errors.add(new PreconditionError("INVALID_TOTAL_SHARE", "Total share must be numeric 100"));
        }

        return errors;
    }

    private Object firstNotNull(Object a, Object b) {
        return a != null ? a : b;
    }
}
