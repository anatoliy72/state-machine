package com.example.state_machine.service.advance.preconditions;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import com.example.state_machine.service.advance.Precondition;
import com.example.state_machine.service.advance.PreconditionError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ensures the final transition of a process is allowed only when {@code voiceScore > 0.95}.
 * <p>
 * The score is resolved in the following order:
 * <ol>
 *   <li>From the incoming payload: {@code data.voiceScore}</li>
 *   <li>Fallback to the process variables map</li>
 * </ol>
 * <p>
 * Applied to the final events of each flow:
 * <ul>
 *   <li><b>SINGLE_OWNER</b>: from {@code BIOMETRY_VERIFIED} via {@code CREATE_ACCOUNT}</li>
 *   <li><b>MULTI_OWNER</b>: from {@code WAITING_FOR_ALL_OWNERS} via {@code CONFIRM_ALL_OWNERS}</li>
 *   <li><b>MINOR</b>: from {@code WAITING_FOR_PARENT_CONSENT} via {@code PARENT_APPROVED}</li>
 *   <li><b>MINOR_TO_REGULAR</b>: from {@code WAITING_FOR_CONVERSION_CONFIRMATION} via {@code COMPLETE_CONVERSION}</li>
 * </ul>
 */
@Component
public class VoiceScoreSatisfiedPrecondition implements Precondition {

    /**
     * Strict threshold for voice score. The score must be strictly greater than this value.
     */
    private static final double THRESHOLD = 0.95d;

    /**
     * Indicates whether this precondition should be evaluated for the given
     * process type/state/event combination.
     *
     * @param type  process type
     * @param state current state
     * @param event triggering event
     * @return {@code true} if this precondition applies to the combination, otherwise {@code false}
     */
    @Override
    public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
        return
                (type == ProcessType.SINGLE_OWNER     && state == ProcessState.BIOMETRY_VERIFIED                  && event == ProcessEvent.CREATE_ACCOUNT) ||
                        (type == ProcessType.MULTI_OWNER      && state == ProcessState.WAITING_FOR_ALL_OWNERS             && event == ProcessEvent.CONFIRM_ALL_OWNERS) ||
                        (type == ProcessType.MINOR            && state == ProcessState.WAITING_FOR_PARENT_CONSENT         && event == ProcessEvent.PARENT_APPROVED) ||
                        (type == ProcessType.MINOR_TO_REGULAR && state == ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION && event == ProcessEvent.COMPLETE_CONVERSION);
    }

    /**
     * Validates that {@code voiceScore} is strictly greater than {@value THRESHOLD}
     * before allowing a finalizing transition.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>{@code payload.get("voiceScore")}</li>
     *   <li>{@code pi.getVariables().get("voiceScore")}</li>
     * </ol>
     *
     * @param pi      current process instance (for variables lookup)
     * @param payload incoming event payload (may be {@code null})
     * @return list of {@link PreconditionError}; empty if the precondition is satisfied
     */
    @Override
    public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
        List<PreconditionError> errors = new ArrayList<>();
        Double score = readScore(payload, pi.getVariables());
        // Strict: allow only when voiceScore > THRESHOLD
        if (score == null || !(score > THRESHOLD)) {
            errors.add(new PreconditionError(
                    "VOICE_SCORE_TOO_LOW",
                    "voiceScore must be > " + THRESHOLD + " before finalization"
            ));
        }
        return errors;
    }

    /**
     * Reads {@code voiceScore} from payload first, then from process variables.
     *
     * @param payload incoming data (may be {@code null})
     * @param vars    process variables (may be {@code null})
     * @return parsed {@link Double} or {@code null} when absent/unparsable
     */
    private Double readScore(Map<String, Object> payload, Map<String, Object> vars) {
        Double fromPayload = toDouble(payload != null ? payload.get("voiceScore") : null);
        if (fromPayload != null) return fromPayload;
        return toDouble(vars != null ? vars.get("voiceScore") : null);
    }

    /**
     * Safely converts an arbitrary object to {@link Double}.
     *
     * @param v value to convert (number or string)
     * @return {@link Double} value or {@code null} if conversion fails
     */
    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception ignore) { return null; }
    }
}
