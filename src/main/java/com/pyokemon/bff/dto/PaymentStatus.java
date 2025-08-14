package com.pyokemon.bff.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum PaymentStatus {
    READY("READY", "결제 대기"),
    DONE("DONE", "결제 완료"),
    CANCELED("CANCELED", "결제 취소"),
    FAILED("FAILED", "결제 실패");

    private final String value;
    private final String displayValue;

    PaymentStatus(String value, String displayValue) {
        this.value = value;
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getDisplayValue() { return displayValue; }

    @JsonCreator
    public static PaymentStatus fromValue(String value) {
        if (value == null) {
            log.warn("Payment status is null, defaulting to READY");
            return READY;
        }
        for (PaymentStatus status : PaymentStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        log.warn("Unknown payment status: {}, defaulting to READY", value);
        return READY;
    }
}
