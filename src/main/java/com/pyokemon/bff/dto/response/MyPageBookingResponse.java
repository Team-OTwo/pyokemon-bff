package com.pyokemon.bff.dto.response;

import com.pyokemon.bff.dto.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyPageBookingResponse {
    private Long bookingId;
    private String eventTitle;
    private String eventDate;
    private String venueName;
    private String thumbnailUrl;
    private Integer totalPrice;
    private String status;
}