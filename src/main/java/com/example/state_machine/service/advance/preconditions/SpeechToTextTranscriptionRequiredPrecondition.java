package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.*;
import com.example.state_machine.service.advance.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SpeechToTextTranscriptionRequiredPrecondition extends BasePrecondition implements Precondition {

    @Value("${workflow.voice-score-threshold:0.95}")
    private double voiceScoreThreshold;

    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return type == ProcessType.MINOR
                && state == ProcessState.SPEECH_TO_TEXT
                && (event == ProcessEvent.PROCESS_SPEECH_TO_TEXT || event == ProcessEvent.BLOCK_FLOW);
    }

    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        log.debug("[Precondition] {} validating. processId={}, state={}, payloadKeys={}",
                getClass().getSimpleName(), pi.getId(), pi.getState(), payload != null ? payload.keySet() : "{}");
        List<PreconditionError> errors = new ArrayList<>();
        Object transcription = read(payload, pi, "transcription");
        log.debug("[Precondition] transcription present={}", !isEmpty(transcription));
        if (isEmpty(transcription)) {
            errors.add(new PreconditionError("transcription", "REQUIRED"));
        }

        Object scoreObj = read(payload, pi, "voiceScore");
        Double score = toDouble(scoreObj);
        log.debug("[Precondition] voiceScore={}, threshold={}", score, voiceScoreThreshold);
        if (score == null) {
            errors.add(new PreconditionError("voiceScore", "REQUIRED"));
        }

        // Decide blocking based on voice score threshold; store decision in payload for guards to consume
        if (payload != null) {
            boolean sttBlocked = (score != null) && score < voiceScoreThreshold;
            payload.put("sttBlocked", sttBlocked);
            log.debug("[Precondition] computed sttBlocked={}", sttBlocked);
        }
        log.debug("[Precondition] {} completed. errorsCount={}", getClass().getSimpleName(), errors.size());
        return errors;
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
