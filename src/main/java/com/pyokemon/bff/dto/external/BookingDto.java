package com.pyokemon.bff.dto.external;

import com.pyokemon.bff.dto.BookingStatus;
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
    private Long tenantId;
    private Long seatId;
    private Long paymentId;
    private BookingStatus status;  // PENDING, BOOKED, CANCELED, FAILED
    private LocalDateTime updatedAt;
}