package com.pyokemon.bff.dto.response;

import com.pyokemon.bff.dto.BookingStatus;
import com.pyokemon.bff.dto.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private String userName;
    private String eventTitle;
    private String eventDate;
    private String venueName;
    private SeatInfo seat;
    private String thumbnailUrl;
    private Integer totalPrice;
    private String status;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatInfo {
        private String className;
        private Integer floor;
        private String row;
        private String col;
    }
}