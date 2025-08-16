package com.example.state_machine.model;

import lombok.Getter;

@Getter
public enum ProcessState {

    // === Common start states ===
    STARTED("s500.1"),

    // === Minor Account Opening Flow ===
    MINOR_OCCUPATION_SCREEN("s530.1"),
    INCOME_SCREEN("s530.2"),
    EXPENSES_SCREEN("s530.3"),
    GENERATE_SCAN("s530.4"),
    SPEECH_TO_TEXT("s530.5"),
    PERFORM_MATCH("s530.6"),
    FACE_RECOGNITION_UPLOAD("s530.7"),
    CUSTOMER_INFO_VALIDATION("s530.8"),
    SIGNATURE_EXAMPLE_SCREEN("s530.9"),
    ACCOUNT_ACTIVITIES_SCREEN("s530.10"),
    STUDENT_PACKAGES_SCREEN("s530.11"),
    VIDEO_SCREEN("s530.12"),
    CUSTOMER_ADDRESS_SCREEN("s530.13"),
    CHOOSE_BRANCH_SCREEN("s530.14"),
    INFORMATION_ACTIVITIES_SCREEN("s530.15"),
    TWO_MORE_QUESTIONS_SCREEN("s530.16"),
    SERVICE_SUBSCRIPTION("s530.17"),
    NO_SERVICE_SUBSCRIPTION("s530.18"),
    FORMS("s530.19"),
    WARNINGS("s530.20"),
    WELCOME("s530.21"),
    BLOCKED("s530.99"); // Состояние для блокировки процесса

    private final String screenCode;

    ProcessState(String screenCode) {
        this.screenCode = screenCode;
    }
}
