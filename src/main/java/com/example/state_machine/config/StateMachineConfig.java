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
        minorToRegularFlow(t);
    }

    // ---------------------------------------------------------------------
    // MINOR ACCOUNT OPENING FLOW
    // ---------------------------------------------------------------------
    private void minorAccountOpeningFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        // Start flow
        t.withExternal()
                .source(ProcessState.STARTED).target(ProcessState.MINOR_OCCUPATION_SCREEN)
                .event(ProcessEvent.START_FLOW).guard(type(ProcessType.MINOR))

        // Cyclic transition MINOR_OCCUPATION_SCREEN <-> INCOME_SCREEN
        .and().withExternal()
                .source(ProcessState.MINOR_OCCUPATION_SCREEN).target(ProcessState.INCOME_SCREEN)
                .event(ProcessEvent.SUBMIT_OCCUPATION)
        .and().withExternal()
                .source(ProcessState.INCOME_SCREEN).target(ProcessState.MINOR_OCCUPATION_SCREEN)
                .event(ProcessEvent.BACK)

        // INCOME_SCREEN -> EXPENSES_SCREEN (only when toContinue=true)
        .and().withExternal()
                .source(ProcessState.INCOME_SCREEN)
                .target(ProcessState.EXPENSES_SCREEN)
                .event(ProcessEvent.CONTINUE_FLOW)
                .guard(toContinue())

        // EXPENSES_SCREEN -> INCOME_SCREEN (when toContinue=false)
        .and().withExternal()
                .source(ProcessState.EXPENSES_SCREEN).target(ProcessState.INCOME_SCREEN)
                .event(ProcessEvent.BACK)

        // EXPENSES_SCREEN -> GENERATE_SCAN (only when toContinue=true)
        .and().withExternal()
                .source(ProcessState.EXPENSES_SCREEN)
                .target(ProcessState.GENERATE_SCAN)
                .event(ProcessEvent.CONTINUE_FLOW)
                .guard(toContinue())

        // GENERATE_SCAN -> SPEECH_TO_TEXT
        .and().withExternal()
                .source(ProcessState.GENERATE_SCAN).target(ProcessState.SPEECH_TO_TEXT)
                .event(ProcessEvent.GENERATE_DOCUMENT_SCAN)

        // SPEECH_TO_TEXT -> stop (when toBlock=true)
        .and().withExternal()
                .source(ProcessState.SPEECH_TO_TEXT).target(ProcessState.BLOCKED)
                .event(ProcessEvent.BLOCK_FLOW).guard(toBlock())

        // SPEECH_TO_TEXT -> PERFORM_MATCH (when toBlock=false)
        .and().withExternal()
                .source(ProcessState.SPEECH_TO_TEXT).target(ProcessState.PERFORM_MATCH)
                .event(ProcessEvent.PROCESS_SPEECH_TO_TEXT).guard(not(toBlock()))

        // PERFORM_MATCH cycle when scanMatch != OK and tries < 3
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH).target(ProcessState.PERFORM_MATCH)
                .event(ProcessEvent.PERFORM_DOCUMENT_MATCH).guard(canRetryMatch())

        // PERFORM_MATCH -> stop when maximum tries exceeded
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH).target(ProcessState.BLOCKED)
                .event(ProcessEvent.BLOCK_FLOW).guard(exceededMatchTries())

        // Parallel processes on successful match
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH).target(ProcessState.FACE_RECOGNITION_UPLOAD)
                .event(ProcessEvent.UPLOAD_FACE_RECOGNITION).guard(scanMatchOk())
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH).target(ProcessState.CUSTOMER_INFO_VALIDATION)
                .event(ProcessEvent.VALIDATE_CUSTOMER_INFO).guard(scanMatchOk())

        // Transition to SIGNATURE_EXAMPLE_SCREEN on successful verification
        .and().withExternal()
                .source(ProcessState.FACE_RECOGNITION_UPLOAD).target(ProcessState.SIGNATURE_EXAMPLE_SCREEN)
                .event(ProcessEvent.SUBMIT_SIGNATURE).guard(oneToManyStatusOk())

        // Screen sequence after successful verification
        .and().withExternal()
                .source(ProcessState.SIGNATURE_EXAMPLE_SCREEN).target(ProcessState.ACCOUNT_ACTIVITIES_SCREEN)
                .event(ProcessEvent.SUBMIT_ACCOUNT_ACTIVITIES)
        .and().withExternal()
                .source(ProcessState.ACCOUNT_ACTIVITIES_SCREEN).target(ProcessState.STUDENT_PACKAGES_SCREEN)
                .event(ProcessEvent.SUBMIT_STUDENT_PACKAGES)
        .and().withExternal()
                .source(ProcessState.STUDENT_PACKAGES_SCREEN).target(ProcessState.VIDEO_SCREEN)
                .event(ProcessEvent.SUBMIT_VIDEO)

        // VIDEO_SCREEN -> INCOME_SCREEN (when toContinue=false)
        .and().withExternal()
                .source(ProcessState.VIDEO_SCREEN).target(ProcessState.INCOME_SCREEN)
                .event(ProcessEvent.BACK).guard(not(toContinue()))

        // VIDEO_SCREEN -> CUSTOMER_ADDRESS_SCREEN (only when toContinue=true)
        .and().withExternal()
                .source(ProcessState.VIDEO_SCREEN)
                .target(ProcessState.CUSTOMER_ADDRESS_SCREEN)
                .event(ProcessEvent.CONTINUE_FLOW)
                .guard(toContinue())

        // Sequence after address
        .and().withExternal()
                .source(ProcessState.CUSTOMER_ADDRESS_SCREEN).target(ProcessState.CHOOSE_BRANCH_SCREEN)
                .event(ProcessEvent.SUBMIT_BRANCH_CHOICE)
        .and().withExternal()
                .source(ProcessState.CHOOSE_BRANCH_SCREEN).target(ProcessState.INFORMATION_ACTIVITIES_SCREEN)
                .event(ProcessEvent.SUBMIT_INFORMATION_ACTIVITIES)
        .and().withExternal()
                .source(ProcessState.INFORMATION_ACTIVITIES_SCREEN).target(ProcessState.TWO_MORE_QUESTIONS_SCREEN)
                .event(ProcessEvent.SUBMIT_ADDITIONAL_QUESTIONS)

        // TWO_MORE_QUESTIONS_SCREEN -> stop (when toBlock=true)
        .and().withExternal()
                .source(ProcessState.TWO_MORE_QUESTIONS_SCREEN).target(ProcessState.BLOCKED)
                .event(ProcessEvent.BLOCK_FLOW).guard(toBlock())

        // Service subscription fork
        .and().withExternal()
                .source(ProcessState.TWO_MORE_QUESTIONS_SCREEN).target(ProcessState.SERVICE_SUBSCRIPTION)
                .event(ProcessEvent.SUBSCRIBE_TO_SERVICE).guard(needsServiceSubscription())
        .and().withExternal()
                .source(ProcessState.TWO_MORE_QUESTIONS_SCREEN).target(ProcessState.NO_SERVICE_SUBSCRIPTION)
                .event(ProcessEvent.DECLINE_SERVICE).guard(not(needsServiceSubscription()))

        // Final sequence
        .and().withExternal()
                .source(ProcessState.SERVICE_SUBSCRIPTION).target(ProcessState.FORMS)
                .event(ProcessEvent.SUBMIT_FORMS)
        .and().withExternal()
                .source(ProcessState.NO_SERVICE_SUBSCRIPTION).target(ProcessState.FORMS)
                .event(ProcessEvent.SUBMIT_FORMS)
        .and().withExternal()
                .source(ProcessState.FORMS).target(ProcessState.WARNINGS)
                .event(ProcessEvent.ACKNOWLEDGE_WARNINGS)
        .and().withExternal()
                .source(ProcessState.WARNINGS).target(ProcessState.WELCOME)
                .event(ProcessEvent.COMPLETE_WELCOME);
    }

    // ---------------------------------------------------------------------
    // MINOR TO REGULAR FLOW
    // ---------------------------------------------------------------------
    private void minorToRegularFlow(StateMachineTransitionConfigurer<ProcessState, ProcessEvent> t) throws Exception {
        // STARTED -> INCOME_SCREEN automatically on process start
        t.withExternal()
                .source(ProcessState.STARTED)
                .target(ProcessState.INCOME_SCREEN)
                .event(ProcessEvent.START_FLOW)
                .guard(type(ProcessType.MINOR_TO_REGULAR))

        // INCOME_SCREEN -> PERFORM_MATCH when income data submitted
        .and().withExternal()
                .source(ProcessState.INCOME_SCREEN)
                .target(ProcessState.PERFORM_MATCH)
                .event(ProcessEvent.SUBMIT_INCOME)
                .guard(type(ProcessType.MINOR_TO_REGULAR))

        // PERFORM_MATCH -> WELCOME if account found
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH)
                .target(ProcessState.WELCOME)
                .event(ProcessEvent.PERFORM_DOCUMENT_MATCH)
                .guard(allOf(type(ProcessType.MINOR_TO_REGULAR), bankBranchAccountExists()))

        // PERFORM_MATCH -> STARTED if account not found
        .and().withExternal()
                .source(ProcessState.PERFORM_MATCH)
                .target(ProcessState.STARTED)
                .event(ProcessEvent.BACK)
                .guard(allOf(type(ProcessType.MINOR_TO_REGULAR), not(bankBranchAccountExists())));
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

    private Guard<ProcessState, ProcessEvent> not(Guard<ProcessState, ProcessEvent> guard) {
        return context -> !guard.evaluate(context);
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
        return context -> {
            Map<Object, Object> variables = context.getExtendedState().getVariables();
            Object value = variables.get("toContinue");
            // Strict check for Boolean.TRUE
            return Boolean.TRUE.equals(value);
        };
    }

    private Guard<ProcessState, ProcessEvent> toBlock() {
        return context -> {
            Boolean toBlock = (Boolean) context.getExtendedState().getVariables().get("toBlock");
            return Boolean.TRUE.equals(toBlock);
        };
    }

    private Guard<ProcessState, ProcessEvent> scanMatchOk() {
        return context -> "OK".equals(context.getExtendedState().getVariables().get("scanMatch"));
    }

    private Guard<ProcessState, ProcessEvent> oneToManyStatusOk() {
        return context -> "OK".equals(context.getExtendedState().getVariables().get("oneToManyStatus"));
    }

    private Guard<ProcessState, ProcessEvent> canRetryMatch() {
        return context -> {
            Integer tries = (Integer) context.getExtendedState().getVariables().getOrDefault("numOfScanMatchTries", 0);
            String scanMatch = (String) context.getExtendedState().getVariables().get("scanMatch");
            return !"OK".equals(scanMatch) && tries < 3;
        };
    }

    private Guard<ProcessState, ProcessEvent> exceededMatchTries() {
        return context -> {
            Integer tries = (Integer) context.getExtendedState().getVariables().getOrDefault("numOfScanMatchTries", 0);
            return tries >= 3;
        };
    }

    private Guard<ProcessState, ProcessEvent> needsServiceSubscription() {
        return context -> {
            String indication = (String) context.getExtendedState().getVariables().get("privateInternetSubscriptionIndication");
            Integer statusCode = (Integer) context.getExtendedState().getVariables().get("servicePartyStatusCode");
            return "0".equals(indication) || Integer.valueOf(1).equals(statusCode);
        };
    }

    private Guard<ProcessState, ProcessEvent> bankBranchAccountExists() {
        return context -> {
            try {
                Map<Object, Object> variables = context.getExtendedState().getVariables();
                if (variables.containsKey("accountDetails")) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> accountDetails = (Map<String, String>) variables.get("accountDetails");
                    String bankId = accountDetails.get("bankId");
                    String branchCode = accountDetails.get("branchCode");
                    String accountNumber = accountDetails.get("accountNumber");

                    if (bankId == null || branchCode == null || accountNumber == null) {
                        log.warn("Missing required account details: bankId={}, branchCode={}, accountNumber={}",
                                bankId, branchCode, accountNumber);
                        return false;
                    }

                    // Read accounts from file
                    try (var is = StateMachineConfig.class.getResourceAsStream("/bank-accounts.csv")) {
                        if (is == null) {
                            log.error("bank-accounts.csv not found in resources");
                            return false;
                        }

                        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                            // Skip header
                            reader.readLine();

                            // Check each line
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String[] parts = line.split(",");
                                if (parts.length >= 3 &&
                                    bankId.equals(parts[0].trim()) &&
                                    branchCode.equals(parts[1].trim()) &&
                                    accountNumber.equals(parts[2].trim())) {
                                    log.info("Account found: bankId={}, branchCode={}, accountNumber={}",
                                            bankId, branchCode, accountNumber);
                                    return true;
                                }
                            }
                        }
                    }
                    log.warn("Account not found: bankId={}, branchCode={}, accountNumber={}",
                            bankId, branchCode, accountNumber);
                } else {
                    log.warn("accountDetails not found in state machine variables");
                }
            } catch (Exception e) {
                log.error("Error checking account existence", e);
            }
            return false;
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
