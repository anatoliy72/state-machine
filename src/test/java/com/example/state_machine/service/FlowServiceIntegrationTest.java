package com.example.state_machine.service;

import com.example.state_machine.config.TestMongoConfig;
import com.example.state_machine.config.TestStateMachineConfig;
import com.example.state_machine.model.*;
import com.example.state_machine.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestStateMachineConfig.class, TestMongoConfig.class})
class FlowServiceIntegrationTest {

    @Autowired
    private FlowService flowService;

    @Autowired
    private ProcessInstanceRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void completeKYCFlow_Success() {
        // Start process
        ProcessInstance process = flowService.startProcess(
            "client123",
            ProcessType.SINGLE_OWNER,
            Map.of("accountType", "CHECKING")
        );
        assertEquals(ProcessState.STARTED, process.getState());

        // Submit personal details
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.SUBMIT_PERSONAL,
            Map.of(
                "firstName", "John",
                "lastName", "Doe",
                "dateOfBirth", "1990-01-01"
            )
        );
        assertEquals(ProcessState.FILL_PERSONAL_DETAILS, process.getState());

        // Submit answers
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.SUBMIT_ANSWERS,
            Map.of(
                "employmentStatus", "EMPLOYED",
                "monthlyIncome", "5000",
                "purposeOfAccount", "SAVINGS"
            )
        );
        assertEquals(ProcessState.KYC_IN_PROGRESS, process.getState());

        // KYC verification
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.KYC_VERIFIED,
            Map.of(
                "verificationId", "kyc123",
                "status", "APPROVED",
                "riskLevel", "LOW"
            )
        );
        assertEquals(ProcessState.WAITING_FOR_BIOMETRY, process.getState());

        // Biometry verification
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.BIOMETRY_SUCCESS,
            Map.of(
                "biometryId", "bio123",
                "matchScore", "0.95",
                "livenessScore", "0.98"
            )
        );
        assertEquals(ProcessState.BIOMETRY_VERIFIED, process.getState());

        // Create account
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.CREATE_ACCOUNT,
            Map.of(
                "accountId", "acc123",
                "iban", "GB123456789",
                "currency", "USD"
            )
        );
        assertEquals(ProcessState.ACCOUNT_CREATED, process.getState());

        // Verify persisted data
        ProcessInstance savedProcess = repository.findById(process.getId())
            .orElseThrow(() -> new NoSuchElementException("Process not found"));

        assertEquals(ProcessState.ACCOUNT_CREATED, savedProcess.getState());
        assertEquals("client123", savedProcess.getClientId());
        assertEquals("John", savedProcess.getVariables().get("firstName"));
        assertEquals("APPROVED", savedProcess.getVariables().get("status"));
        assertEquals("0.95", savedProcess.getVariables().get("matchScore"));
        assertEquals("GB123456789", savedProcess.getVariables().get("iban"));
    }

    @Test
    void completeMinorAccountFlow_Success() {
        // Start process for minor
        ProcessInstance process = flowService.startProcess(
            "minor123",
            ProcessType.MINOR,
            Map.of("age", "14")
        );
        assertEquals(ProcessState.STARTED, process.getState());

        // Submit personal details
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.SUBMIT_PERSONAL,
            Map.of(
                "firstName", "John Jr",
                "lastName", "Doe",
                "dateOfBirth", "2011-01-01"
            )
        );
        assertEquals(ProcessState.FILL_PERSONAL_DETAILS, process.getState());

        // Submit parent details
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.REQUEST_PARENT_CONSENT,
            Map.of(
                "parentFirstName", "John Sr",
                "parentLastName", "Doe",
                "parentId", "parent123",
                "relationship", "FATHER"
            )
        );
        assertEquals(ProcessState.WAITING_FOR_PARENT_CONSENT, process.getState());

        // Parent approval received
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.PARENT_APPROVED,
            Map.of(
                "consentDocument", "consent123.pdf",
                "consentDate", "2025-08-09"
            )
        );
        assertEquals(ProcessState.ACCOUNT_CREATED_LIMITED, process.getState());

        // Verify persisted data
        ProcessInstance savedProcess = repository.findById(process.getId())
            .orElseThrow(() -> new NoSuchElementException("Process not found"));

        assertEquals(ProcessState.ACCOUNT_CREATED_LIMITED, savedProcess.getState());
        assertEquals(ProcessType.MINOR, savedProcess.getType());
        assertEquals("14", savedProcess.getVariables().get("age"));
        assertEquals("parent123", savedProcess.getVariables().get("parentId"));
        assertEquals("consent123.pdf", savedProcess.getVariables().get("consentDocument"));
    }

    @Test
    void completeMultiOwnerFlow_Success() {
        // Start process for multiple owners
        ProcessInstance process = flowService.startProcess(
            "business123",
            ProcessType.MULTI_OWNER,
            Map.of("companyName", "ABC Corp")
        );
        assertEquals(ProcessState.STARTED, process.getState());

        // Add first owner
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.ADD_OWNER,
            Map.of(
                "ownerId", "owner1",
                "firstName", "John",
                "lastName", "Doe",
                "share", "60",
                "role", "CEO"
            )
        );
        assertEquals(ProcessState.WAITING_FOR_ALL_OWNERS, process.getState());

        // Add second owner
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.ADD_OWNER,
            Map.of(
                "ownerId", "owner2",
                "firstName", "Jane",
                "lastName", "Doe",
                "share", "40",
                "role", "CFO"
            )
        );
        assertEquals(ProcessState.WAITING_FOR_ALL_OWNERS, process.getState());

        // Confirm all owners
        process = flowService.handleEvent(
            process.getId(),
            ProcessEvent.CONFIRM_ALL_OWNERS,
            Map.of(
                "totalOwners", "2",
                "totalShare", "100",
                "confirmationDate", "2025-08-09"
            )
        );
        assertEquals(ProcessState.ACCOUNT_CREATED, process.getState());

        // Verify persisted data
        ProcessInstance savedProcess = repository.findById(process.getId())
            .orElseThrow(() -> new NoSuchElementException("Process not found"));

        assertEquals(ProcessState.ACCOUNT_CREATED, savedProcess.getState());
        assertEquals(ProcessType.MULTI_OWNER, savedProcess.getType());
        assertEquals("ABC Corp", savedProcess.getVariables().get("companyName"));
        assertEquals("2", savedProcess.getVariables().get("totalOwners"));
        assertEquals("100", savedProcess.getVariables().get("totalShare"));
    }
}
