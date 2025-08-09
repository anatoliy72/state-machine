package com.example.state_machine.config;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessState;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;

@TestConfiguration
@EnableStateMachineFactory
@Profile("test")
public class TestStateMachineConfig extends EnumStateMachineConfigurerAdapter<ProcessState, ProcessEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<ProcessState, ProcessEvent> states) throws Exception {
        states
            .withStates()
                .initial(ProcessState.STARTED)
                .states(EnumSet.allOf(ProcessState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> transitions) throws Exception {
        transitions
            .withExternal()
                .source(ProcessState.STARTED)
                .target(ProcessState.FILL_PERSONAL_DETAILS)
                .event(ProcessEvent.SUBMIT_PERSONAL)
                .and()
            .withExternal()
                .source(ProcessState.KYC_IN_PROGRESS)
                .target(ProcessState.WAITING_FOR_BIOMETRY)
                .event(ProcessEvent.KYC_VERIFIED)
                .and()
            .withExternal()
                .source(ProcessState.WAITING_FOR_BIOMETRY)
                .target(ProcessState.BIOMETRY_VERIFIED)
                .event(ProcessEvent.BIOMETRY_SUCCESS)
                .and()
            .withExternal()
                .source(ProcessState.WAITING_FOR_PARENT_CONSENT)
                .target(ProcessState.ACCOUNT_CREATED_LIMITED)
                .event(ProcessEvent.PARENT_APPROVED)
                .and()
            .withExternal()
                .source(ProcessState.WAITING_FOR_ALL_OWNERS)
                .target(ProcessState.ACCOUNT_CREATED)
                .event(ProcessEvent.CONFIRM_ALL_OWNERS)
                .and()
            .withExternal()
                .source(ProcessState.WAITING_FOR_CONVERSION_CONFIRMATION)
                .target(ProcessState.ACCOUNT_CONVERTED_TO_REGULAR)
                .event(ProcessEvent.COMPLETE_CONVERSION);
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<ProcessState, ProcessEvent> config) throws Exception {
        config
            .withConfiguration()
                .autoStartup(true)
                .listener(listener());
    }

    @Bean
    public StateMachineListener<ProcessState, ProcessEvent> listener() {
        return new StateMachineListenerAdapter<ProcessState, ProcessEvent>() {
            @Override
            public void stateChanged(State<ProcessState, ProcessEvent> from, State<ProcessState, ProcessEvent> to) {
                if (from != null) {
                    System.out.printf("State change from %s to %s%n", from.getId(), to.getId());
                }
            }
        };
    }
}
