package com.example.state_machine.config;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.transition.Transition;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
@Slf4j
public class StateMachineConfig extends StateMachineConfigurerAdapter<ProcessState, ProcessEvent> {
    private static final Logger logger = LoggerFactory.getLogger(StateMachineConfig.class);

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

    /**
     * Business logic: Single Owner Account Opening
     * <ul>
     *   <li>After KYC is verified, move to biometry step</li>
     *   <li>After biometry is successful, move to account creation</li>
     *   <li>After account is created, finish flow</li>
     * </ul>
     */
    private void registerSingleOwnerFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> transitions) throws Exception {
        transitions
            .withExternal().source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY).event(ProcessEvent.KYC_VERIFIED)
            .and().withExternal().source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED).event(ProcessEvent.BIOMETRY_SUCCESS)
            .and().withExternal().source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.ACCOUNT_CREATED).event(ProcessEvent.CREATE_ACCOUNT);
    }

    /**
     * Business logic: Multi Owner Account Opening
     * <ul>
     *   <li>After all owners fill personal details and answer questions, move to KYC</li>
     *   <li>After KYC and biometry, add owners</li>
     *   <li>After all owners confirmed, create account</li>
     * </ul>
     */
    private void registerMultiOwnerFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> transitions) throws Exception {
        transitions
            .withExternal().source(ProcessState.STARTED).target(ProcessState.FILL_PERSONAL_DETAILS).event(ProcessEvent.START_FLOW)
            .and().withExternal().source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS).event(ProcessEvent.SUBMIT_PERSONAL)
            .and().withExternal().source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS).event(ProcessEvent.SUBMIT_ANSWERS)
            .and().withExternal().source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY).event(ProcessEvent.KYC_VERIFIED)
            .and().withExternal().source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED).event(ProcessEvent.BIOMETRY_SUCCESS)
            .and().withExternal().source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_ALL_OWNERS).event(ProcessEvent.ADD_OWNER)
            .and().withExternal().source(ProcessState.WAITING_FOR_ALL_OWNERS).target(ProcessState.ACCOUNT_CREATED).event(ProcessEvent.CONFIRM_ALL_OWNERS);
    }

    /**
     * Business logic: Minor Account Opening
     * <ul>
     *   <li>After biometry, request parent consent</li>
     *   <li>After parent approval, create limited account</li>
     *   <li>After account creation, identify minor account</li>
     * </ul>
     */
    private void registerMinorFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> transitions) throws Exception {
        transitions
            .withExternal().source(ProcessState.STARTED).target(ProcessState.FILL_PERSONAL_DETAILS).event(ProcessEvent.START_FLOW)
            .and().withExternal().source(ProcessState.FILL_PERSONAL_DETAILS).target(ProcessState.ANSWER_ACCOUNT_QUESTIONS).event(ProcessEvent.SUBMIT_PERSONAL)
            .and().withExternal().source(ProcessState.ANSWER_ACCOUNT_QUESTIONS).target(ProcessState.KYC_IN_PROGRESS).event(ProcessEvent.SUBMIT_ANSWERS)
            .and().withExternal().source(ProcessState.KYC_IN_PROGRESS).target(ProcessState.WAITING_FOR_BIOMETRY).event(ProcessEvent.KYC_VERIFIED)
            .and().withExternal().source(ProcessState.WAITING_FOR_BIOMETRY).target(ProcessState.BIOMETRY_VERIFIED).event(ProcessEvent.BIOMETRY_SUCCESS)
            .and().withExternal().source(ProcessState.BIOMETRY_VERIFIED).target(ProcessState.WAITING_FOR_PARENT_CONSENT).event(ProcessEvent.REQUEST_PARENT_CONSENT)
            .and().withExternal().source(ProcessState.WAITING_FOR_PARENT_CONSENT).target(ProcessState.ACCOUNT_CREATED_LIMITED).event(ProcessEvent.PARENT_APPROVED)
            .and().withExternal().source(ProcessState.ACCOUNT_CREATED_LIMITED).target(ProcessState.MINOR_ACCOUNT_IDENTIFIED).event(ProcessEvent.CREATE_ACCOUNT);
    }

    /**
     * Business logic: Minor to Regular Account Conversion
     * <ul>
     *   <li>After confirmation, convert account to regular</li>
     * </ul>
     */
    private void registerMinorToRegularFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> transitions) throws Exception {
        transitions
            .withExternal().source(ProcessState.MINOR_ACCOUNT_IDENTIFIED).target(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION).event(ProcessEvent.CONFIRM_CONVERSION)
            .and().withExternal().source(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION).target(ProcessState.ACCOUNT_CONVERTED_TO_REGULAR).event(ProcessEvent.COMPLETE_CONVERSION);
    }

    // --- Utility: Common transitions for all flows (if needed) ---
    // Example: transitions for BACK or UPDATE_VARIABLES can be added here if shared
    // private void registerCommonTransitions(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> transitions) throws Exception {
    //     transitions.withExternal().source(...).target(...).event(...);
    // }

    @Bean
    public StateMachinePersist<ProcessState, ProcessEvent, String> stateMachinePersist() {
        return new StateMachinePersist<>() {

            @Override
            public void write(StateMachineContext<ProcessState, ProcessEvent> stateMachineContext, String s) throws Exception {
                // In-memory, no-op
                logger.info("State machine context written for process type: {}", s);
            }

            @Override
            public StateMachineContext<ProcessState, ProcessEvent> read(String contextObj) {
                return null;
            }
        };
    }

    @Bean
    public StateMachineListenerAdapter<ProcessState, ProcessEvent> stateMachineListener() {
        return new StateMachineListenerAdapter<>() {
            @Override
            public void transition(Transition<ProcessState, ProcessEvent> transition) {
                if (transition.getSource() != null && transition.getTarget() != null) {
                    logger.info("Transition: {} -> {} on {}", transition.getSource().getId(), transition.getTarget().getId(), transition.getTrigger().getEvent());
                }
            }
            @Override
            public void stateMachineError(StateMachine<ProcessState, ProcessEvent> stateMachine, Exception exception) {
                logger.error("State machine error: ", exception);
            }
        };
    }
}
