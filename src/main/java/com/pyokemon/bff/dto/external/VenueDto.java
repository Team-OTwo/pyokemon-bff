package com.pyokemon.bff.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueDto {
    private Long id;
    private String name;
    private String address;
    private Integer capacity;
    private String description;
}