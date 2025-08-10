package com.example.state_machine.config;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.transition.Transition;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
@Slf4j
public class StateMachineConfig extends StateMachineConfigurerAdapter<ProcessState, ProcessEvent> {

    public static final String EXT_TYPE = "processType";

    @Override
    public void configure(StateMachineStateConfigurer<ProcessState, ProcessEvent> states) throws Exception {
        states.withStates()
                .initial(ProcessState.STARTED)
                .states(EnumSet.allOf(ProcessState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> transitions) throws Exception {
        registerSingleOwnerFlow(transitions);
        registerMultiOwnerFlow(transitions);
        registerMinorFlow(transitions);
        registerMinorToRegularFlow(transitions);
    }

    // ---------- SINGLE OWNER: happy path ----------
    private void registerSingleOwnerFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.START_FLOW).guard(guardType(ProcessType.SINGLE_OWNER))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(guardType(ProcessType.SINGLE_OWNER))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(guardType(ProcessType.SINGLE_OWNER))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.ACCOUNT_CREATED)
                .event(ProcessEvent.CREATE_ACCOUNT).guard(guardType(ProcessType.SINGLE_OWNER));
    }

    // ---------- MULTI OWNER ----------
    private void registerMultiOwnerFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.START_FLOW).guard(guardType(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .event(ProcessEvent.SUBMIT_PERSONAL).guard(guardType(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.SUBMIT_ANSWERS).guard(guardType(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(guardType(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(guardType(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_ALL_OWNERS)
                .event(ProcessEvent.ADD_OWNER).guard(guardType(ProcessType.MULTI_OWNER))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_ALL_OWNERS).target(ProcessState.ACCOUNT_CREATED)
                .event(ProcessEvent.CONFIRM_ALL_OWNERS).guard(guardType(ProcessType.MULTI_OWNER));
    }

    // ---------- MINOR ----------
    private void registerMinorFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.START_FLOW).guard(guardType(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS)
                .event(ProcessEvent.SUBMIT_PERSONAL).guard(guardType(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS)
                .event(ProcessEvent.SUBMIT_ANSWERS).guard(guardType(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED).guard(guardType(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS).guard(guardType(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_PARENT_CONSENT)
                .event(ProcessEvent.REQUEST_PARENT_CONSENT).guard(guardType(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_PARENT_CONSENT).target(ProcessState.ACCOUNT_CREATED_LIMITED)
                .event(ProcessEvent.PARENT_APPROVED).guard(guardType(ProcessType.MINOR))
                .and().withExternal()
                .source(ProcessState.ACCOUNT_CREATED_LIMITED).target(ProcessState.MINOR_ACCOUNT_IDENTIFIED)
                .event(ProcessEvent.CREATE_ACCOUNT).guard(guardType(ProcessType.MINOR));
    }

    // ---------- MINOR -> REGULAR ----------
    private void registerMinorToRegularFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.MINOR_ACCOUNT_IDENTIFIED).target(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION)
                .event(ProcessEvent.CONFIRM_CONVERSION).guard(guardType(ProcessType.MINOR_TO_REGULAR))
                .and().withExternal()
                .source(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION).target(ProcessState.ACCOUNT_CONVERTED_TO_REGULAR)
                .event(ProcessEvent.COMPLETE_CONVERSION).guard(guardType(ProcessType.MINOR_TO_REGULAR));
    }

    // ---------- Guards ----------
    private Guard<ProcessState, ProcessEvent> guardType(ProcessType expected) {
        return ctx -> {
            Object v = ctx.getExtendedState().getVariables().get(EXT_TYPE);
            return v == expected || (v instanceof ProcessType && v.equals(expected));
        };
    }

    // ---------- Persist & Listener ----------
    @Bean
    public StateMachinePersist<ProcessState, ProcessEvent, String> stateMachinePersist() {
        return new StateMachinePersist<>() {
            @Override public void write(StateMachineContext<ProcessState, ProcessEvent> c, String s) { log.info("Persist {}", s); }
            @Override public StateMachineContext<ProcessState, ProcessEvent> read(String contextObj) { return null; }
        };
    }

    @Bean
    public StateMachineListenerAdapter<ProcessState, ProcessEvent> stateMachineListener() {
        return new StateMachineListenerAdapter<>() {
            @Override
            public void transition(Transition<ProcessState, ProcessEvent> tr) {
                if (tr.getSource()!=null && tr.getTarget()!=null) {
                    log.info("Transition: {} -> {} on {}", tr.getSource().getId(), tr.getTarget().getId(),
                            tr.getTrigger()!=null ? tr.getTrigger().getEvent() : null);
                }
            }
            @Override
            public void stateMachineError(StateMachine<ProcessState, ProcessEvent> sm, Exception e) {
                log.error("State machine error", e);
            }
        };
    }
}
