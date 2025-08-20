package com.pyokemon.bff.dto.response;


import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
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
    public static class UserInfo {
        private String name;
    }

    @Data
    @Builder
    public static class EventInfo {
        private String title;
        private String thumbnailUrl;
        private String eventDate;
        private VenueInfo venue;

        @Data
        @Builder
        public static class VenueInfo {
            private String name;
        }
    }

    @Data
    @Builder
    public static class SeatInfo {
        private String className;
        private Integer floor;
        private String row;
        private String col;
    }

    @Data
    @Builder
    public static class PaymentInfo {
        private String method;
        private String status;
        private String paidAt;
        private Long amount;
    }
}