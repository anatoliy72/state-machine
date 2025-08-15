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
    @Value("${workflow.strict-guards:true}")
    private boolean strictGuards;

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
        minorAccountOpeningFlow(t);
    }

    // ---------------------------------------------------------------------
    // MINOR ACCOUNT OPENING FLOW
    // ---------------------------------------------------------------------
    private void minorAccountOpeningFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        // Start flow
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.MINOR_OCCUPATION_SCREEN)
                .event(ProcessEvent.START_FLOW).guard(type(ProcessType.MINOR))

        // Occupation screen -> Income screen
        .and().withExternal()
                .source(ProcessState.MINOR_OCCUPATION_SCREEN).target(ProcessState.INCOME_SCREEN)
                .event(ProcessEvent.SUBMIT_OCCUPATION).guard(type(ProcessType.MINOR))

        // Income screen -> Expenses screen (if continue)
        .and().withExternal()
                .source(ProcessState.INCOME_SCREEN).target(ProcessState.EXPENSES_SCREEN)
                .event(ProcessEvent.SUBMIT_INCOME).guard(allOf(type(ProcessType.MINOR), toContinue()))

        // Income screen -> Income screen (if not continue - stay on same screen)
        .and().withExternal()
                .source(ProcessState.INCOME_SCREEN).target(ProcessState.INCOME_SCREEN)
                .event(ProcessEvent.SUBMIT_INCOME).guard(allOf(type(ProcessType.MINOR), not(toContinue())))

        // Expenses screen -> Generate scan (if continue)
        .and().withExternal()
                .source(ProcessState.EXPENSES_SCREEN).target(ProcessState.GENERATE_SCAN)
                .event(ProcessEvent.SUBMIT_EXPENSES).guard(allOf(type(ProcessType.MINOR), toContinue()))

        // Expenses screen -> Expenses screen (if not continue - stay on same screen)
        .and().withExternal()
                .source(ProcessState.EXPENSES_SCREEN).target(ProcessState.EXPENSES_SCREEN)
                .event(ProcessEvent.SUBMIT_EXPENSES).guard(allOf(type(ProcessType.MINOR), not(toContinue())))

        // Generate scan -> Speech to text
        .and().withExternal()
                .source(ProcessState.GENERATE_SCAN).target(ProcessState.SPEECH_TO_TEXT)
                .event(ProcessEvent.GENERATE_DOCUMENT_SCAN).guard(type(ProcessType.MINOR))

        // Speech to text -> Perform match (if not blocked)
        .and().withExternal()
                .source(ProcessState.SPEECH_TO_TEXT).target(ProcessState.PERFORM_MATCH)
                .event(ProcessEvent.PROCESS_SPEECH_TO_TEXT).guard(allOf(type(ProcessType.MINOR), not(toBlock())))

        // Speech to text -> STOP (if blocked)
        .and().withExternal()
                .source(ProcessState.SPEECH_TO_TEXT).target(ProcessState.STARTED)
                .event(ProcessEvent.BLOCK_FLOW).guard(allOf(type(ProcessType.MINOR), toBlock()))

        // Perform match -> Face recognition + Customer validation (parallel processing)
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH).target(ProcessState.FACE_RECOGNITION_UPLOAD)
                .event(ProcessEvent.PERFORM_DOCUMENT_MATCH).guard(allOf(type(ProcessType.MINOR), scanMatchOk()))

        // Face recognition -> Customer validation (parallel processing)
        .and().withExternal()
                .source(ProcessState.FACE_RECOGNITION_UPLOAD).target(ProcessState.CUSTOMER_INFO_VALIDATION)
                .event(ProcessEvent.UPLOAD_FACE_RECOGNITION).guard(type(ProcessType.MINOR))

        // Customer validation -> Signature screen (if one-to-many OK)
        .and().withExternal()
                .source(ProcessState.CUSTOMER_INFO_VALIDATION).target(ProcessState.SIGNATURE_EXAMPLE_SCREEN)
                .event(ProcessEvent.VALIDATE_CUSTOMER_INFO).guard(allOf(type(ProcessType.MINOR), oneToManyStatusOk()))

        // Signature screen -> Account activities screen
        .and().withExternal()
                .source(ProcessState.SIGNATURE_EXAMPLE_SCREEN).target(ProcessState.ACCOUNT_ACTIVITIES_SCREEN)
                .event(ProcessEvent.SUBMIT_SIGNATURE).guard(type(ProcessType.MINOR))

        // Account activities -> Student packages screen
        .and().withExternal()
                .source(ProcessState.ACCOUNT_ACTIVITIES_SCREEN).target(ProcessState.STUDENT_PACKAGES_SCREEN)
                .event(ProcessEvent.SUBMIT_ACCOUNT_ACTIVITIES).guard(type(ProcessType.MINOR))

        // Student packages -> Video screen
        .and().withExternal()
                .source(ProcessState.STUDENT_PACKAGES_SCREEN).target(ProcessState.VIDEO_SCREEN)
                .event(ProcessEvent.SUBMIT_STUDENT_PACKAGES).guard(type(ProcessType.MINOR))

        // Video screen -> Customer address screen (if continue)
        .and().withExternal()
                .source(ProcessState.VIDEO_SCREEN).target(ProcessState.CUSTOMER_ADDRESS_SCREEN)
                .event(ProcessEvent.SUBMIT_VIDEO).guard(allOf(type(ProcessType.MINOR), toContinue()))

        // Video screen -> Video screen (if not continue - stay on same screen)
        .and().withExternal()
                .source(ProcessState.VIDEO_SCREEN).target(ProcessState.VIDEO_SCREEN)
                .event(ProcessEvent.SUBMIT_VIDEO).guard(allOf(type(ProcessType.MINOR), not(toContinue())))

        // Customer address -> Choose branch screen
        .and().withExternal()
                .source(ProcessState.CUSTOMER_ADDRESS_SCREEN).target(ProcessState.CHOOSE_BRANCH_SCREEN)
                .event(ProcessEvent.SUBMIT_ADDRESS).guard(type(ProcessType.MINOR))

        // Choose branch -> Information activities screen
        .and().withExternal()
                .source(ProcessState.CHOOSE_BRANCH_SCREEN).target(ProcessState.INFORMATION_ACTIVITIES_SCREEN)
                .event(ProcessEvent.SUBMIT_BRANCH_CHOICE).guard(type(ProcessType.MINOR))

        // Information activities -> Two more questions screen
        .and().withExternal()
                .source(ProcessState.INFORMATION_ACTIVITIES_SCREEN).target(ProcessState.TWO_MORE_QUESTIONS_SCREEN)
                .event(ProcessEvent.SUBMIT_INFORMATION_ACTIVITIES).guard(type(ProcessType.MINOR))

        // Two more questions -> Service subscription (if not blocked)
        .and().withExternal()
                .source(ProcessState.TWO_MORE_QUESTIONS_SCREEN).target(ProcessState.SERVICE_SUBSCRIPTION)
                .event(ProcessEvent.SUBMIT_ADDITIONAL_QUESTIONS).guard(allOf(type(ProcessType.MINOR), not(toBlock())))

        // Two more questions -> STOP (if blocked)
        .and().withExternal()
                .source(ProcessState.TWO_MORE_QUESTIONS_SCREEN).target(ProcessState.STARTED)
                .event(ProcessEvent.BLOCK_FLOW).guard(allOf(type(ProcessType.MINOR), toBlock()))

        // Service subscription -> Forms (if service needed)
        .and().withExternal()
                .source(ProcessState.SERVICE_SUBSCRIPTION).target(ProcessState.FORMS)
                .event(ProcessEvent.SUBSCRIBE_TO_SERVICE).guard(allOf(type(ProcessType.MINOR), serviceNeeded()))

        // Service subscription -> No service subscription (if service not needed)
        .and().withExternal()
                .source(ProcessState.SERVICE_SUBSCRIPTION).target(ProcessState.NO_SERVICE_SUBSCRIPTION)
                .event(ProcessEvent.DECLINE_SERVICE).guard(allOf(type(ProcessType.MINOR), not(serviceNeeded())))

        // No service subscription -> Forms
        .and().withExternal()
                .source(ProcessState.NO_SERVICE_SUBSCRIPTION).target(ProcessState.FORMS)
                .event(ProcessEvent.DECLINE_SERVICE).guard(type(ProcessType.MINOR))

        // Forms -> Warnings
        .and().withExternal()
                .source(ProcessState.FORMS).target(ProcessState.WARNINGS)
                .event(ProcessEvent.SUBMIT_FORMS).guard(type(ProcessType.MINOR))

        // Warnings -> Welcome
        .and().withExternal()
                .source(ProcessState.WARNINGS).target(ProcessState.WELCOME)
                .event(ProcessEvent.ACKNOWLEDGE_WARNINGS).guard(type(ProcessType.MINOR))

        // Welcome -> STOP (completion)
        .and().withExternal()
                .source(ProcessState.WELCOME).target(ProcessState.STARTED)
                .event(ProcessEvent.COMPLETE_WELCOME).guard(type(ProcessType.MINOR))

        // Retry logic for scan match (up to 3 tries)
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH).target(ProcessState.PERFORM_MATCH)
                .event(ProcessEvent.PERFORM_DOCUMENT_MATCH).guard(allOf(type(ProcessType.MINOR), scanMatchFail(), scanMatchTriesLessThan3()))

        // Scan match fail after 3 tries -> STOP
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH).target(ProcessState.STARTED)
                .event(ProcessEvent.BLOCK_FLOW).guard(allOf(type(ProcessType.MINOR), scanMatchFail(), scanMatchTries3OrMore()))

        // Customer validation -> Signature screen (if one-to-many not OK)
        .and().withExternal()
                .source(ProcessState.CUSTOMER_INFO_VALIDATION).target(ProcessState.SIGNATURE_EXAMPLE_SCREEN)
                .event(ProcessEvent.VALIDATE_CUSTOMER_INFO).guard(allOf(type(ProcessType.MINOR), not(oneToManyStatusOk())))

        // BACK transitions (limited)
        .and().withExternal()
                .source(ProcessState.INCOME_SCREEN).target(ProcessState.MINOR_OCCUPATION_SCREEN)
                .event(ProcessEvent.BACK).guard(type(ProcessType.MINOR))
        .and().withExternal()
                .source(ProcessState.EXPENSES_SCREEN).target(ProcessState.INCOME_SCREEN)
                .event(ProcessEvent.BACK).guard(type(ProcessType.MINOR))
        .and().withExternal()
                .source(ProcessState.VIDEO_SCREEN).target(ProcessState.STUDENT_PACKAGES_SCREEN)
                .event(ProcessEvent.BACK).guard(type(ProcessType.MINOR));
    }

    // ---------------------------------------------------------------------
    // Guards
    // ---------------------------------------------------------------------

    private Guard<ProcessState, ProcessEvent> type(ProcessType expected) {
        return ctx -> {
            Object v = ctx.getExtendedState().getVariables().get(EXT_TYPE);
            if (v == expected) return true;
            if (v instanceof ProcessType p) return p == expected;
            if (v instanceof String s) return expected.name().equalsIgnoreCase(s.trim());
            return false;
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

    // Flow control guards
    private Guard<ProcessState, ProcessEvent> toContinue() {
        return ctx -> {
            if (!strictGuards) return true;
            Object v = ctx.getExtendedState().getVariables().get("toContinue");
            if (v instanceof Boolean b) return b;
            return Boolean.parseBoolean(Objects.toString(v, "true"));
        };
    }

    private Guard<ProcessState, ProcessEvent> toBlock() {
        return ctx -> {
            if (!strictGuards) return false;
            Object v = ctx.getExtendedState().getVariables().get("toBlock");
            if (v instanceof Boolean b) return b;
            return Boolean.parseBoolean(Objects.toString(v, "false"));
        };
    }

    // Scan match guards
    private Guard<ProcessState, ProcessEvent> scanMatchOk() {
        return ctx -> {
            if (!strictGuards) return true;
            Object v = ctx.getExtendedState().getVariables().get("scanMatch");
            return "OK".equalsIgnoreCase(Objects.toString(v, ""));
        };
    }

    private Guard<ProcessState, ProcessEvent> scanMatchFail() {
        return ctx -> {
            if (!strictGuards) return false;
            Object v = ctx.getExtendedState().getVariables().get("scanMatch");
            return "FAIL".equalsIgnoreCase(Objects.toString(v, ""));
        };
    }

    private Guard<ProcessState, ProcessEvent> scanMatchTriesLessThan3() {
        return ctx -> {
            if (!strictGuards) return true;
            Object v = ctx.getExtendedState().getVariables().get("numOfScanMatchTries");
            if (v instanceof Number n) return n.intValue() < 3;
            try {
                int tries = Integer.parseInt(Objects.toString(v, "0"));
                return tries < 3;
            } catch (NumberFormatException e) {
                return true;
            }
        };
    }

    private Guard<ProcessState, ProcessEvent> scanMatchTries3OrMore() {
        return ctx -> {
            if (!strictGuards) return false;
            Object v = ctx.getExtendedState().getVariables().get("numOfScanMatchTries");
            if (v instanceof Number n) return n.intValue() >= 3;
            try {
                int tries = Integer.parseInt(Objects.toString(v, "0"));
                return tries >= 3;
            } catch (NumberFormatException e) {
                return false;
            }
        };
    }

    // One-to-many status guard
    private Guard<ProcessState, ProcessEvent> oneToManyStatusOk() {
        return ctx -> {
            if (!strictGuards) return true;
            Object v = ctx.getExtendedState().getVariables().get("oneToManyStatus");
            return "OK".equalsIgnoreCase(Objects.toString(v, ""));
        };
    }

    // Service subscription guards
    private Guard<ProcessState, ProcessEvent> serviceNeeded() {
        return ctx -> {
            if (!strictGuards) return true;
            Map<Object, Object> v = ctx.getExtendedState().getVariables();
            Object privateInternet = v.get("privateInternetSubscriptionIndication");
            Object servicePartyStatus = v.get("servicePartyStatusCode");
            
            // Check if privateInternetSubscriptionIndication == '0' OR servicePartyStatusCode == 1
            boolean privateInternetZero = "0".equals(Objects.toString(privateInternet, ""));
            boolean servicePartyStatusOne = Objects.equals(servicePartyStatus, 1) || "1".equals(Objects.toString(servicePartyStatus, ""));
            
            return privateInternetZero || servicePartyStatusOne;
        };
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
