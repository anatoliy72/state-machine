package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SpeechToTextTranscriptionRequiredPrecondition extends BasePrecondition implements Precondition {

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.SPEECH_TO_TEXT
                && (event == ProcessEvent.PROCESS_SPEECH_TO_TEXT || event == ProcessEvent.BLOCK_FLOW);
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        if (isEmpty(read(payload, pi, "transcription"))) {
            errors.add(new PreconditionError("transcription", "REQUIRED"));
        }
        return errors;
    }
}
