package com.pyokemon.bff.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBookingListResponse {
    private Long bookingId;
    private String eventTitle;
    private LocalDateTime eventDate;
    private String venueName;
    private String tenantName;
    private String thumbnailUrl;
}
