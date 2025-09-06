package com.pyokemon.bff.dto.external;

import com.pyokemon.bff.dto.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto {
    private Long id;
    private Long amount;
    private String method;
    private PaymentStatus status;
    private LocalDateTime updatedAt;
}