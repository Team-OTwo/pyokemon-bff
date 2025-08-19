package com.pyokemon.bff.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventScheduleDto {
    private Long eventScheduleId;
    private Long eventId;
    private Long venueId;
    private LocalDateTime eventDate;
}