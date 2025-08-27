package com.pyokemon.bff.dto.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class IdsRequest {
    private List<Long> ids;
}
