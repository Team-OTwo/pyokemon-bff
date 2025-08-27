package com.pyokemon.bff.dto.external;

import lombok.Data;

@Data
public class BookingCountDto {
    private Long eventScheduleId;
    private Long ticketCount;
}
