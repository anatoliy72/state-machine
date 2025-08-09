package com.example.state_machine.service;

import com.example.state_machine.model.ProcessEvent;
import com.example.state_machine.model.ProcessInstance;
import com.example.state_machine.model.ProcessState;
import com.example.state_machine.model.ProcessType;
import com.example.state_machine.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowService {
    private final ProcessInstanceRepository repository;
    private final StateMachineFactory<ProcessState, ProcessEvent> stateMachineFactory;
    private final StateMachinePersist<ProcessState, ProcessEvent, String> stateMachinePersist;

    @Transactional
    public ProcessInstance startProcess(String clientId, ProcessType type, Map<String, Object> initialData) {
        Assert.hasText(clientId, "clientId must not be blank");
        Assert.notNull(type, "type must not be null");
        ProcessInstance instance = ProcessInstance.builder()
                .clientId(clientId)
                .type(type)
                .state(ProcessState.STARTED)
                .variables(initialData != null ? initialData : Map.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        repository.save(instance);
        return instance;
    }

    @Transactional
    public ProcessInstance handleEvent(String processId, ProcessEvent event, Map<String, Object> data) {
        ProcessInstance instance = repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));
        StateMachine<ProcessState, ProcessEvent> sm = stateMachineFactory.getStateMachine(processId);
        sm.getExtendedState().getVariables().putAll(instance.getVariables());
        sm.start();
        boolean accepted = sm.sendEvent(event);
        if (!accepted) throw new IllegalStateException("Event not accepted: " + event);
        instance.setState(sm.getState().getId());
        if (data != null) instance.getVariables().putAll(data);
        instance.setUpdatedAt(Instant.now());
        repository.save(instance);
        return instance;
    }

    @Transactional
    public ProcessInstance updateVariables(String processId, Map<String, Object> updates) {
        ProcessInstance instance = repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));
        if (updates != null) instance.getVariables().putAll(updates);
        instance.setUpdatedAt(Instant.now());
        repository.save(instance);
        return instance;
    }

    @Transactional(readOnly = true)
    public ProcessInstance getProcess(String processId) {
        return repository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));
    }
}
