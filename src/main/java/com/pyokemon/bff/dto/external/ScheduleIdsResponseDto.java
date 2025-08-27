package com.pyokemon.bff.dto.external;

import lombok.Data;
import java.util.List;

@Data
public class ScheduleIdsResponseDto {
    private List<Long> scheduleIds;
}
