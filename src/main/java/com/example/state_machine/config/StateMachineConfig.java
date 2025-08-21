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
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.transition.Transition;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableStateMachineFactory
@Slf4j
public class StateMachineConfig extends StateMachineConfigurerAdapter<ProcessState, ProcessEvent> {

    public static final String EXT_TYPE = "processType";

    @Override
    public void configure(StateMachineConfigurationConfigurer<ProcessState, ProcessEvent> config) throws Exception {
        config.withConfiguration().listener(stateMachineListener());
    }

    @Override
    public void configure(StateMachineStateConfigurer<ProcessState, ProcessEvent> states) throws Exception {
        states.withStates()
                .initial(ProcessState.STARTED)
                .states(EnumSet.of(ProcessState.STARTED, ProcessState.MINOR_OCCUPATION_SCREEN, ProcessState.INCOME_SCREEN));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.MINOR_OCCUPATION_SCREEN)
                .event(ProcessEvent.MINOR_OCCUPATION_SCREEN).guard(type(ProcessType.MINOR))

    // forward to minor_occupation_screen
    .and().withExternal()
        .source(ProcessState.MINOR_OCCUPATION_SCREEN).target(ProcessState.MINOR_OCCUPATION_SCREEN)
        .event(ProcessEvent.UPDATE).guard(type(ProcessType.MINOR))

    // update minor_occupation_screen
    .and().withExternal()
        .source(ProcessState.MINOR_OCCUPATION_SCREEN).target(ProcessState.INCOME_SCREEN)
        .event(ProcessEvent.CONTINUE).guard(type(ProcessType.MINOR))

    // forward to income_screen
    .and().withExternal()
        .source(ProcessState.MINOR_OCCUPATION_SCREEN).target(ProcessState.INCOME_SCREEN)
        .event(ProcessEvent.INCOME_SCREEN).guard(type(ProcessType.MINOR))

    // update income_screen
    .and().withExternal()
        .source(ProcessState.INCOME_SCREEN).target(ProcessState.INCOME_SCREEN)
        .event(ProcessEvent.UPDATE).guard(type(ProcessType.MINOR))

    // forward to minor_occupation_screen
    .and().withExternal()
        .source(ProcessState.INCOME_SCREEN).target(ProcessState.MINOR_OCCUPATION_SCREEN)
        .event(ProcessEvent.BACK).guard(type(ProcessType.MINOR));
    }

    private Guard<ProcessState, ProcessEvent> type(ProcessType expected) {
        return ctx -> {
            Object v = ctx.getExtendedState().getVariables().get(EXT_TYPE);
            if (v == expected) return true;
            if (v instanceof ProcessType p) return p == expected;
            if (v instanceof String s) return expected.name().equalsIgnoreCase(s.trim());
            return false;
        };
    }

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
