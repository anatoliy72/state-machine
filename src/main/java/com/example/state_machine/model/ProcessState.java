package com.example.state_machine.model;

import lombok.Getter;

@Getter
public enum ProcessState {

    // === Common start states ===
    STARTED("s500.1"),

    // === Minor Account Opening Flow (based on OnBoardingM17) ===
    MINOR_OCCUPATION_SCREEN("s530.1"),
    INCOME_SCREEN("s530.2");

    private final String screenCode;

    ProcessState(String screenCode) {
        this.screenCode = screenCode;
    }
}
