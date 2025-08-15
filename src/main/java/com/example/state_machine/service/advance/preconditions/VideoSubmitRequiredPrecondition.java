package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class VideoSubmitRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.VIDEO_SCREEN
                && event == ProcessEvent.SUBMIT_VIDEO;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        boolean toContinue = asBoolean(read(payload, pi, "toContinue"), true);
        if (toContinue && isEmpty(read(payload, pi, "videoFile"))) {
            errors.add(new PreconditionError("videoFile", "REQUIRED"));
        }
        return errors;
    }
}
