package com.pyokemon.bff.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDetailResponse {
    private Long bookingId;
    private String status;
    private LocalDateTime createdAt;
    private UserInfo user;
    private EventInfo event;
    private SeatInfo seat;
    private PaymentInfo payment;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String name;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventInfo {
        private String title;
        private String thumbnailUrl;
        private String eventDate;
        private VenueInfo venue;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VenueInfo {
            private String name;
        }
    }
    
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
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        private String method;
        private String status;
        private LocalDateTime paidAt;
        private Integer amount;
    }
}