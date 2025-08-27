package com.pyokemon.bff.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TenantDashboardResponseDto {
    private Data data;

    @Getter
    @Builder
    public static class Data {
        private List<EventItem> events;
        private Summary summary;
    }

    @Getter
    @Builder
    public static class EventItem {
        private String title;
        private String venueName;
        private LocalDateTime eventDate;
        private Long ticketCount;
    }

    @Getter
    @Builder
    public static class Summary {
        private Long totalRevenue;
        private Long activeEventCount;
        private Long totalTicketsSold;
    }
}
