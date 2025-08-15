package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FaceUploadRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.FACE_RECOGNITION_UPLOAD
                && event == ProcessEvent.UPLOAD_FACE_RECOGNITION;
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        if (isEmpty(read(payload, pi, "faceImage"))) {
            errors.add(new PreconditionError("faceImage", "REQUIRED"));
        }
        return errors;
    }
}
