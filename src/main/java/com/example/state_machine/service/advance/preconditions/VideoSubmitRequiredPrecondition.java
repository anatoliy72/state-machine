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
// public class VideoSubmitRequiredPrecondition extends BasePrecondition implements Precondition {

//     @Override
//     public boolean supports(ProcessType type, ProcessState state, ProcessEvent event) {
//         return type == ProcessType.MINOR
//                 && state == ProcessState.VIDEO_SCREEN
//                 && event == ProcessEvent.SUBMIT_VIDEO;
//     }

//     @Override
//     public List<PreconditionError> validate(ProcessInstance pi, Map<String, Object> payload) {
//         log.debug("[Precondition] {} validating. processId={}, state={}, payloadKeys={}", getClass().getSimpleName(), pi.getId(), pi.getState(), payload != null ? payload.keySet() : "{}");
//         List<PreconditionError> errors = new ArrayList<>();
//         boolean toContinue = asBoolean(read(payload, pi, "toContinue"), true);
//         Object videoFile = read(payload, pi, "videoFile");
//         log.debug("[Precondition] toContinue={}, videoFile present={}", toContinue, videoFile != null);
//         if (toContinue && isEmpty(videoFile)) {
//             errors.add(new PreconditionError("videoFile", "REQUIRED"));
//         }
//         log.debug("[Precondition] {} completed. errorsCount={}", getClass().getSimpleName(), errors.size());
//         return errors;
//     }
// }
