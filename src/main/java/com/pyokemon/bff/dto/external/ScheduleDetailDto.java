package com.pyokemon.bff.dto.external;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduleDetailDto {
    private Long eventScheduleId;
    private String title;
    private String venueName;
    private LocalDateTime eventDate;
}
