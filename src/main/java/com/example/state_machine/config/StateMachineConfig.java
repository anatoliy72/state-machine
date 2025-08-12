package com.example.state_machine.config;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.transition.Transition;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableStateMachineFactory
@Slf4j
public class StateMachineConfig extends StateMachineConfigurerAdapter<ProcessState, ProcessEvent> {

    /** Key of process type in ExtendedState. */
    public static final String EXT_TYPE = "processType";

    /** Enable/disable non-critical business guard checks. */
    @Value("${workflow.strict-guards:false}")
    private boolean strictGuards;

    /** Voice score is always strict: must be strictly greater than this value. */
    private static final double MIN_VOICE_SCORE = 0.95d;

    // ---------------------------------------------------------------------
    // Basic configuration
    // ---------------------------------------------------------------------

    @Override
    public void configure(StateMachineConfigurationConfigurer<ProcessState, ProcessEvent> config) throws Exception {
        config.withConfiguration().listener(stateMachineListener());
    }

    @Override
    public void configure(StateMachineStateConfigurer<ProcessState, ProcessEvent> states) throws Exception {
        states.withStates()
                .initial(ProcessState.STARTED)
                .states(EnumSet.allOf(ProcessState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        singleOwnerFlowWithKycBranch(t);
        multiOwnerFlow(t);
        minorFlow(t);
        minorToRegularFlow(t);
        backTransitionsEarlyOnly(t); // BACK allowed only before KYC answers
    }

    // ---------------------------------------------------------------------
    // SINGLE OWNER with KYC branching (US passport details if isUSCitizen=true)
    // ---------------------------------------------------------------------
    private void singleOwnerFlowWithKycBranch(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .event(ProcessEvent.START_FLOW).guard(type(ProcessType.SINGLE_OWNER))

                // Branch after KYC answers:
                //  - if isUSCitizen -> US_PASSPORT_DETAILS
                //  - else           -> KYC_IN_PROGRESS
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.US_PASSPORT_DETAILS)
                .event(ProcessEvent.SUBMIT_ANSWERS).guard(allOf(type(ProcessType.SINGLE_OWNER), isUsCitizen()))
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.SUBMIT_ANSWERS).guard(allOf(type(ProcessType.SINGLE_OWNER), not(isUsCitizen())))

                // Submit US passport → KYC starts
                .and().withExternal()
                .source(ProcessState.US_PASSPORT_DETAILS).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.SUBMIT_US_PASSPORT).guard(type(ProcessType.SINGLE_OWNER))

                // KYC → Biometry → Final
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(allOf(type(ProcessType.SINGLE_OWNER), kycApproved()))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(allOf(type(ProcessType.SINGLE_OWNER), biometryPassed()))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.ACCOUNT_CREATED)
                .event(ProcessEvent.CREATE_ACCOUNT).guard(allOf(type(ProcessType.SINGLE_OWNER), voiceScoreStrict()));
    }

    // ---------------------------------------------------------------------
    // MULTI OWNER
    // ---------------------------------------------------------------------
    private void multiOwnerFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.START_FLOW).guard(type(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .event(ProcessEvent.SUBMIT_PERSONAL).guard(type(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.SUBMIT_ANSWERS).guard(type(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(allOf(type(ProcessType.MULTI_OWNER), kycApproved()))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(allOf(type(ProcessType.MULTI_OWNER), biometryPassed()))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_ALL_OWNERS)
                .event(ProcessEvent.ADD_OWNER).guard(type(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_ALL_OWNERS).target(ProcessState.ACCOUNT_CREATED)
                .event(ProcessEvent.CONFIRM_ALL_OWNERS).guard(allOf(type(ProcessType.MULTI_OWNER), ownersComplete(), voiceScoreStrict()));
    }

    // ---------------------------------------------------------------------
    // MINOR
    // ---------------------------------------------------------------------
    private void minorFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.START_FLOW).guard(type(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .event(ProcessEvent.SUBMIT_PERSONAL).guard(type(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.SUBMIT_ANSWERS).guard(type(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(allOf(type(ProcessType.MINOR), kycApproved()))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(allOf(type(ProcessType.MINOR), biometryPassed()))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_PARENT_CONSENT)
                .event(ProcessEvent.REQUEST_PARENT_CONSENT).guard(type(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_PARENT_CONSENT).target(ProcessState.ACCOUNT_CREATED_LIMITED)
                .event(ProcessEvent.PARENT_APPROVED).guard(allOf(type(ProcessType.MINOR), parentConsent()));
    }

    // ---------------------------------------------------------------------
    // MINOR → REGULAR
    // ---------------------------------------------------------------------
    private void minorToRegularFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.MINOR_ACCOUNT_IDENTIFIED).target(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION)
                .event(ProcessEvent.CONFIRM_CONVERSION).guard(type(ProcessType.MINOR_TO_REGULAR))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION).target(ProcessState.ACCOUNT_CONVERTED_TO_REGULAR)
                .event(ProcessEvent.COMPLETE_CONVERSION).guard(allOf(type(ProcessType.MINOR_TO_REGULAR), voiceScoreStrict(), conversionConfirmed()));
    }

    // ---------------------------------------------------------------------
    // BACK transitions — ONLY before KYC answers have been submitted
    // ---------------------------------------------------------------------
    private void backTransitionsEarlyOnly(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        // SINGLE_OWNER: ANSWER_ACCOUNT_QUESTIONS -> STARTED
        t.withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.STARTED)
                .event(ProcessEvent.BACK).guard(type(ProcessType.SINGLE_OWNER))

                // MULTI_OWNER: ANSWER_ACCOUNT_QUESTIONS -> FILL_PERSONAL_DETAILS -> STARTED
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.BACK).guard(type(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.STARTED)
                .event(ProcessEvent.BACK).guard(type(ProcessType.MULTI_OWNER))

                // MINOR: ANSWER_ACCOUNT_QUESTIONS -> FILL_PERSONAL_DETAILS -> STARTED
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.BACK).guard(type(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.STARTED)
                .event(ProcessEvent.BACK).guard(type(ProcessType.MINOR));

        // ВАЖНО: никаких BACK после SUBMIT_ANSWERS:
        //  - НЕТ переходов из US_PASSPORT_DETAILS по BACK
        //  - НЕТ переходов из KYC_IN_PROGRESS по BACK
        //  - НЕТ BACK после биометрии или финальных шагов
    }

    // ---------------------------------------------------------------------
    // Guards
    // ---------------------------------------------------------------------

    private Guard<ProcessState, ProcessEvent> type(ProcessType expected) {
        return ctx -> {
            Object v = ctx.getExtendedState().getVariables().get(EXT_TYPE);
            return v == expected || (v instanceof ProcessType && v.equals(expected));
        };
    }

    private Guard<ProcessState, ProcessEvent> isUsCitizen() {
        return ctx -> {
            Object v = ctx.getExtendedState().getVariables().get("isUSCitizen");
            if (v instanceof Boolean b) return b;
            return Boolean.parseBoolean(Objects.toString(v, "false"));
        };
    }

    private Guard<ProcessState, ProcessEvent> not(Guard<ProcessState, ProcessEvent> g) {
        return ctx -> g == null || !g.evaluate(ctx);
    }

    private Guard<ProcessState, ProcessEvent> allOf(Guard<ProcessState, ProcessEvent>... guards) {
        return ctx -> {
            for (Guard<ProcessState, ProcessEvent> g : guards) {
                if (g == null || !g.evaluate(ctx)) return false;
            }
            return true;
        };
    }

    // soft guards (enabled only if strictGuards=true)

    private Guard<ProcessState, ProcessEvent> kycApproved() {
        return ctx -> {
            if (!strictGuards) return true;
            Object status = ctx.getExtendedState().getVariables().get("status");
            return "APPROVED".equalsIgnoreCase(Objects.toString(status, ""));
        };
    }

    private Guard<ProcessState, ProcessEvent> biometryPassed() {
        return ctx -> {
            if (!strictGuards) return true;
            Map<Object, Object> v = ctx.getExtendedState().getVariables();
            Object match = v.get("match");
            if (match instanceof Boolean b && b) return true;
            Double matchScore = toDouble(v.get("matchScore"));
            return matchScore != null && matchScore >= 0.90d;
        };
    }

    private Guard<ProcessState, ProcessEvent> ownersComplete() {
        return ctx -> {
            if (!strictGuards) return true;
            Map<Object, Object> v = ctx.getExtendedState().getVariables();
            Double totalShare = toDouble(v.get("totalShare"));
            if (totalShare != null) return Math.abs(totalShare - 100d) < 1e-6;
            String s = Objects.toString(v.get("totalShare"), "");
            return "100".equals(s);
        };
    }

    private Guard<ProcessState, ProcessEvent> parentConsent() {
        return ctx -> {
            if (!strictGuards) return true;
            String doc = Objects.toString(ctx.getExtendedState().getVariables().get("consentDocument"), "");
            return !doc.isBlank();
        };
    }

    private Guard<ProcessState, ProcessEvent> conversionConfirmed() {
        return ctx -> {
            if (!strictGuards) return true;
            Object converted = ctx.getExtendedState().getVariables().get("converted");
            if (converted instanceof Boolean b) return b;
            return Boolean.parseBoolean(Objects.toString(converted, "false"));
        };
    }

    // Hard guard (always enforced)
    private Guard<ProcessState, ProcessEvent> voiceScoreStrict() {
        return ctx -> {
            Double score = toDouble(ctx.getExtendedState().getVariables().get("voiceScore"));
            return score != null && score > MIN_VOICE_SCORE; // strictly greater than 0.95
        };
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    // ---------------------------------------------------------------------
    // Persist & Listener
    // ---------------------------------------------------------------------

    @Bean
    public StateMachinePersist<ProcessState, ProcessEvent, String> stateMachinePersist() {
        return new StateMachinePersist<>() {
            private final Map<String, StateMachineContext<ProcessState, ProcessEvent>> store = new ConcurrentHashMap<>();
            @Override public void write(StateMachineContext<ProcessState, ProcessEvent> context, String key) { store.put(key, context); }
            @Override public StateMachineContext<ProcessState, ProcessEvent> read(String key) { return store.get(key); }
        };
    }

    @Bean
    public StateMachineListenerAdapter<ProcessState, ProcessEvent> stateMachineListener() {
        return new StateMachineListenerAdapter<>() {
            @Override
            public void transition(Transition<ProcessState, ProcessEvent> tr) {
                if (tr.getSource()!=null && tr.getTarget()!=null) {
                    log.info("Transition: {} -> {} on {}",
                            tr.getSource().getId(),
                            tr.getTarget().getId(),
                            tr.getTrigger()!=null ? tr.getTrigger().getEvent() : null);
                }
            }
            @Override
            public void stateMachineError(StateMachine<ProcessState, ProcessEvent> sm, Exception e) {
                log.error("State machine error [{}]: {}", sm.getId(), e.getMessage(), e);
            }
        };
    }
}
