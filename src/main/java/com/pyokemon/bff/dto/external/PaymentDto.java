package com.pyokemon.bff.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto {
    private Long id;
    private Long bookingId;
    private Integer amount;
    private String method;  // CREDIT_CARD, BANK_TRANSFER, MOBILE
    private String status;  // PENDING, PAID, FAILED, REFUNDED
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}