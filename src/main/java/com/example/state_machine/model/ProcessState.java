package com.example.state_machine.model;

import lombok.Getter;

@Getter
public enum ProcessState {

    // === Common start states (500.x) ===
    STARTED("s500.1"),

    // === Single Owner Flow (510.x) ===
    US_PASSPORT_DETAILS("s510.15"),
    KYC_IN_PROGRESS("s510.1"),
    WAITING_FOR_BIOMETRY("s510.2"),
    BIOMETRY_VERIFIED("s510.3"),
    ACCOUNT_CREATED("s510.4"),

    // === Multi Owner Flow (520.x) ===
    FILL_PERSONAL_DETAILS("s520.1"),
    ANSWER_ACCOUNT_QUESTIONS("s520.2"),
    WAITING_FOR_ALL_OWNERS("s520.3"),

    // === Minor Flow (530.x) ===
    WAITING_FOR_PARENT_CONSENT("s530.1"),
    ACCOUNT_CREATED_LIMITED("s530.2"),
    MINOR_ACCOUNT_IDENTIFIED("s530.3"),

    // === Conversion Flow (540.x) ===
    WAITING_FOR_CONVERSION_CONFIRMATION("s540.1"),
    ACCOUNT_CONVERTED_TO_REGULAR("s540.2");

    private final String screenCode;

    ProcessState(String screenCode) {
        this.screenCode = screenCode;
    }
}
