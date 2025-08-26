package com.pyokemon.bff.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum BookingStatus {
    PENDING("PENDING", "예매 대기"),
    BOOKED("BOOKED", "예매 완료"),
    CANCELED("CANCELED", "예매 취소"),
    FAILED("FAILED", "예매 실패");

    private final String value;
    private final String displayValue;

    BookingStatus(String value, String displayValue) {
        this.value = value;
        this.displayValue = displayValue;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getDisplayValue() { return displayValue; }

    @JsonCreator
    public static BookingStatus fromValue(String value) {
        if (value == null) {
            log.warn("Booking status is null, defaulting to PENDING");
            return PENDING;
        }
        for (BookingStatus status : BookingStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        log.warn("Unknown Booking status: {}, defaulting to PENDDING", value);
        return PENDING;
    }
}
