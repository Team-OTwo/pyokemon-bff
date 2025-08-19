package com.pyokemon.bff.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long eventId;
    private String eventTitle;
    private String eventDate;   // yyyy-MM-dd 등 표시용
    private String venueName;
    private String thumbnailUrl;
    private java.util.List<BookingItem> items;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BookingItem {
        private Long bookingId;
        private String userName;
        private SeatInfo seat;
        private Long totalPrice;
        private String status; // "결제완료" 등 표시용
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SeatInfo {
        private String className;
        private Integer floor;
        private String row;
        private String col;
    }
}



