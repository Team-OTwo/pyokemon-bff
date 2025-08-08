package com.pyokemon.bff.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatSelectionResponse {
    private EventInfo event;
    private List<SeatClassInfo> seatClasses;
    private LocalDateTime lastUpdatedAt;
    private List<ChangedSeatInfo> changedSeats;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventInfo {
        private Long eventScheduleId;
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
    public static class SeatClassInfo {
        private String className;
        private Integer priority;
        private Integer price;
        private Integer availableSeats;
        private Integer totalSeats;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangedSeatInfo {
        private String seatId;
        private String status;  // AVAILABLE, BOOKED, HELD
    }
}