package com.pyokemon.bff.dto.response;

import com.pyokemon.bff.dto.BookingStatus;
import com.pyokemon.bff.dto.PaymentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingDetailResponse {
    private Long bookingId;
    private BookingStatus status;
    private LocalDateTime createdAt;
    private UserInfo user;
    private EventInfo event;
    private SeatInfo seat;
    private PaymentInfo payment;

    @Data
    public static class UserInfo {
        private String name;
    }

    @Data
    public static class EventInfo {
        private String title;
        private String thumbnailUrl;
        private String eventDate;
        private VenueInfo venue;

        @Data
        public static class VenueInfo {
            private String name;
        }
    }

    @Data
    public static class SeatInfo {
        private String className;
        private Integer floor;
        private String row;
        private String col;
    }

    @Data
    public static class PaymentInfo {
        private String method;
        private PaymentStatus status;
        private String paidAt;
        private Integer amount;
    }
}