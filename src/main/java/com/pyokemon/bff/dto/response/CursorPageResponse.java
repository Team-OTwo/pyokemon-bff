package com.pyokemon.bff.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CursorPageResponse<T> {
    private List<T> content;
    private Long nextCursor;
    private Boolean hasMore;
}
