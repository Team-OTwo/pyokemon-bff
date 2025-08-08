package com.pyokemon.bff.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {
    private Long id;
    private Long accountId;
    private Long eventScheduleId;
    private Long seatId;
    private Long paymentId;
    private String status;  // BOOKED, PAID, CANCELLED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}