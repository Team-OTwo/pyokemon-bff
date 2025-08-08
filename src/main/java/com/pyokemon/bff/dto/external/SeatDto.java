package com.pyokemon.bff.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatDto {
    private Long id;
    private Long venueId;
    private Long seatClassId;
    private Integer floor;
    private String row;
    private String col;
    private String seatNumber;  // 통합 좌석 번호 (예: "VIP-D-10")
}