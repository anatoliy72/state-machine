// package com.example.state_machine.service.advance.preconditions;

// import com.example.state_machine.model.*;
// import com.example.state_machine.service.advance.*;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Component;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;

// @Component
// @Slf4j
// public class WarningsAcknowledgeRequiredPrecondition extends BasePrecondition implements Precondition {

//     @Override
//     public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
//         return type == ProcessType.MINOR
//                 && state == ProcessState.WARNINGS
//                 && event == ProcessEvent.ACKNOWLEDGE_WARNINGS;
//     }

//     @Override
//     public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
//         log.debug("[Precondition] {} validating. processId={}, state={}, payloadKeys={}",
//                 getClass().getSimpleName(), pi.getId(), pi.getState(), payload != null ? payload.keySet() : "{}");
//         List<PreconditionError> errors = new ArrayList<>();

//         Object ack = read(payload, pi, "warningsAcknowledged");
//         if (ack == null) {
//             ack = read(payload, pi, "warningsRead");
//         }
//         boolean acknowledged = asBoolean(ack, false);

//         boolean riskAcknowledged = asBoolean(read(payload, pi, "riskAcknowledged"), false);
//         boolean parentalConsent = asBoolean(read(payload, pi, "parentalConsent"), false);

//         log.debug("[Precondition] acknowledged={}, riskAcknowledged={}, parentalConsent={}",
//                 acknowledged, riskAcknowledged, parentalConsent);

//         // Consider satisfied when:
//         // 1) warningsAcknowledged/warningsRead is true, OR
//         // 2) both riskAcknowledged and parentalConsent are true
//         if (!(acknowledged || (riskAcknowledged && parentalConsent))) {
//             errors.add(new PreconditionError("warningsAcknowledged", "REQUIRED"));
//         }
//         log.debug("[Precondition] {} completed. errorsCount={}", getClass().getSimpleName(), errors.size());
//         return errors;
//     }
// }
