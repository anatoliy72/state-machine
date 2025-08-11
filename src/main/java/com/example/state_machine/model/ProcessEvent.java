package com.example.state_machine.model;

public enum ProcessEvent {
    START_FLOW,
    SUBMIT_PERSONAL,
    SUBMIT_ANSWERS,
    KYC_VERIFIED,
    BIOMETRY_SUCCESS,
    ADD_OWNER,
    CONFIRM_ALL_OWNERS,
    REQUEST_PARENT_CONSENT,
    PARENT_APPROVED,
    CREATE_ACCOUNT,
    CONFIRM_CONVERSION,
    COMPLETE_CONVERSION,
    BACK // ⬅️ добавлено
}
