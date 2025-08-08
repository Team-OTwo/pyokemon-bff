package com.pyokemon.bff.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatClassDto {
    private Long id;
    private String className;
    private Integer priority;  // 낮을수록 높은 등급
    private Long venueId;
}