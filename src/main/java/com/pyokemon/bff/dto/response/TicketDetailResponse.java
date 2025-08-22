package com.pyokemon.bff.dto.response;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class TicketDetailResponse {

    private Long bookingId;
    private EventInfo event;
    private SeatInfo seat;
    private String tenantName;

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
}
