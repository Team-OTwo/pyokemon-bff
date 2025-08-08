package com.pyokemon.bff.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceDto {
    private Long id;
    private Long eventScheduleId;
    private Long seatClassId;
    private Integer price;
}