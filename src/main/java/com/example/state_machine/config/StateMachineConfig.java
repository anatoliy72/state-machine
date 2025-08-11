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

    public static final String EXT_TYPE = "processType";

    @Value("${workflow.strict-guards:false}")
    private boolean strictGuards;

    @Value("${workflow.voice-score-threshold:0.95}")
    private double voiceScoreThreshold;

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
        registerSingleOwnerFlow(t);
        registerMultiOwnerFlow(t);
        registerMinorFlow(t);
        registerMinorToRegularFlow(t);
        registerBackTransitions(t); // ⬅️ обратные переходы
    }

    // ---------- SINGLE OWNER ----------
    private void registerSingleOwnerFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.START_FLOW).guard(all(guardType(ProcessType.SINGLE_OWNER)))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(all(guardType(ProcessType.SINGLE_OWNER), guardKycApproved()))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(all(guardType(ProcessType.SINGLE_OWNER), guardBiometryPassed()))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.ACCOUNT_CREATED)
                .event(ProcessEvent.CREATE_ACCOUNT).guard(all(guardType(ProcessType.SINGLE_OWNER), guardVoiceScore()));
    }

    // ---------- MULTI OWNER ----------
    private void registerMultiOwnerFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.START_FLOW).guard(all(guardType(ProcessType.MULTI_OWNER)))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .event(ProcessEvent.SUBMIT_PERSONAL).guard(all(guardType(ProcessType.MULTI_OWNER)))
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.SUBMIT_ANSWERS).guard(all(guardType(ProcessType.MULTI_OWNER)))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(all(guardType(ProcessType.MULTI_OWNER), guardKycApproved()))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(all(guardType(ProcessType.MULTI_OWNER), guardBiometryPassed()))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_ALL_OWNERS)
                .event(ProcessEvent.ADD_OWNER).guard(all(guardType(ProcessType.MULTI_OWNER)))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_ALL_OWNERS).target(ProcessState.ACCOUNT_CREATED)
                .event(ProcessEvent.CONFIRM_ALL_OWNERS).guard(all(guardType(ProcessType.MULTI_OWNER), guardOwnersComplete(), guardVoiceScore()));
    }

    // ---------- MINOR ----------
    private void registerMinorFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.START_FLOW).guard(all(guardType(ProcessType.MINOR)))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .event(ProcessEvent.SUBMIT_PERSONAL).guard(all(guardType(ProcessType.MINOR)))
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.SUBMIT_ANSWERS).guard(all(guardType(ProcessType.MINOR)))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(all(guardType(ProcessType.MINOR), guardKycApproved()))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(all(guardType(ProcessType.MINOR), guardBiometryPassed()))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_PARENT_CONSENT)
                .event(ProcessEvent.REQUEST_PARENT_CONSENT).guard(all(guardType(ProcessType.MINOR)))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_PARENT_CONSENT).target(ProcessState.ACCOUNT_CREATED_LIMITED)
                .event(ProcessEvent.PARENT_APPROVED).guard(all(guardType(ProcessType.MINOR), guardParentConsent()))
                // финальный шаг ветки MINOR
                .and().withExternal()
                .source(ProcessState.ACCOUNT_CREATED_LIMITED).target(ProcessState.MINOR_ACCOUNT_IDENTIFIED)
                .event(ProcessEvent.CREATE_ACCOUNT).guard(all(guardType(ProcessType.MINOR), guardMinorAccountIdentified()));
    }

    // ---------- MINOR -> REGULAR ----------
    private void registerMinorToRegularFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.MINOR_ACCOUNT_IDENTIFIED).target(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION)
                .event(ProcessEvent.CONFIRM_CONVERSION).guard(all(guardType(ProcessType.MINOR_TO_REGULAR)))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION).target(ProcessState.ACCOUNT_CONVERTED_TO_REGULAR)
                .event(ProcessEvent.COMPLETE_CONVERSION).guard(all(guardType(ProcessType.MINOR_TO_REGULAR), guardVoiceScore(), guardConversionConfirmed()));
    }

    // ---------- BACK transitions ----------
    private void registerBackTransitions(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        // MULTI_OWNER & MINOR common backs between early steps
        t.withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.BACK).guard(anyType(ProcessType.MULTI_OWNER, ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.STARTED)
                .event(ProcessEvent.BACK).guard(anyType(ProcessType.MULTI_OWNER, ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .event(ProcessEvent.BACK).guard(anyType(ProcessType.MULTI_OWNER, ProcessType.MINOR))
                // WAITING_FOR_BIOMETRY <-> BIOMETRY_VERIFIED for all who use them
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.BACK).guard(anyType(ProcessType.MULTI_OWNER, ProcessType.MINOR, ProcessType.SINGLE_OWNER))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.BACK).guard(anyType(ProcessType.MULTI_OWNER, ProcessType.MINOR, ProcessType.SINGLE_OWNER))
                // MULTI_OWNER specific
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_ALL_OWNERS).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BACK).guard(guardType(ProcessType.MULTI_OWNER))
                // MINOR specific
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_PARENT_CONSENT).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BACK).guard(guardType(ProcessType.MINOR))
                // SINGLE_OWNER: earliest back
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.STARTED)
                .event(ProcessEvent.BACK).guard(guardType(ProcessType.SINGLE_OWNER))
                // MINOR_TO_REGULAR: back before completing conversion
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION).target(ProcessState.MINOR_ACCOUNT_IDENTIFIED)
                .event(ProcessEvent.BACK).guard(guardType(ProcessType.MINOR_TO_REGULAR));
    }

    // ---------- Guards helpers ----------

    private Guard<ProcessState, ProcessEvent> guardType(ProcessType expected) {
        return ctx -> {
            Object v = ctx.getExtendedState().getVariables().get(EXT_TYPE);
            return v == expected || (v instanceof ProcessType && v.equals(expected));
        };
    }

    private Guard<ProcessState, ProcessEvent> anyType(ProcessType... allowed) {
        return ctx -> {
            Object v = ctx.getExtendedState().getVariables().get(EXT_TYPE);
            if (!(v instanceof ProcessType p)) return false;
            for (ProcessType a : allowed) if (p == a) return true;
            return false;
        };
    }

    private Guard<ProcessState, ProcessEvent> guardKycApproved() {
        return ctx -> {
            if (!strictGuards) return true;
            Object status = ctx.getExtendedState().getVariables().get("status");
            return "APPROVED".equalsIgnoreCase(Objects.toString(status, ""));
        };
    }

    private Guard<ProcessState, ProcessEvent> guardBiometryPassed() {
        return ctx -> {
            if (!strictGuards) return true;
            Map<Object, Object> v = ctx.getExtendedState().getVariables();
            Object match = v.get("match");
            if (match instanceof Boolean b && b) return true;
            Double matchScore = toDouble(v.get("matchScore"));
            return matchScore != null && matchScore >= 0.90d;
        };
    }

    private Guard<ProcessState, ProcessEvent> guardOwnersComplete() {
        return ctx -> {
            if (!strictGuards) return true;
            Map<Object, Object> v = ctx.getExtendedState().getVariables();
            Double totalShare = toDouble(v.get("totalShare"));
            if (totalShare != null) return Math.abs(totalShare - 100d) < 1e-6;
            String s = Objects.toString(v.get("totalShare"), "");
            return "100".equals(s);
        };
    }

    private Guard<ProcessState, ProcessEvent> guardParentConsent() {
        return ctx -> {
            if (!strictGuards) return true;
            String doc = Objects.toString(ctx.getExtendedState().getVariables().get("consentDocument"), "");
            return !doc.isBlank();
        };
    }

    private Guard<ProcessState, ProcessEvent> guardMinorAccountIdentified() {
        return ctx -> {
            if (!strictGuards) return true;
            String acc = Objects.toString(ctx.getExtendedState().getVariables().get("accLimited"), "");
            return !acc.isBlank();
        };
    }

    private Guard<ProcessState, ProcessEvent> guardConversionConfirmed() {
        return ctx -> {
            if (!strictGuards) return true;
            Object converted = ctx.getExtendedState().getVariables().get("converted");
            if (converted instanceof Boolean b) return b;
            return Boolean.parseBoolean(Objects.toString(converted, "false"));
        };
    }

    private Guard<ProcessState, ProcessEvent> guardVoiceScore() {
        return ctx -> {
            if (!strictGuards) return true;
            Double score = toDouble(ctx.getExtendedState().getVariables().get("voiceScore"));
            return score != null && score > voiceScoreThreshold; // строго >
        };
    }

    private Guard<ProcessState, ProcessEvent> all(Guard<ProcessState, ProcessEvent>... guards) {
        return ctx -> {
            for (Guard<ProcessState, ProcessEvent> g : guards) {
                if (g == null || !g.evaluate(ctx)) return false;
            }
            return true;
        };
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    // ---------- Persist & Listener ----------
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
