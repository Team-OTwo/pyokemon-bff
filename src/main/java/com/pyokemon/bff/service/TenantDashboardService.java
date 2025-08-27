package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.external.*;
import com.pyokemon.bff.dto.response.TenantDashboardResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantDashboardService {

    private final WebClient bookingServiceWebClient;
    private final WebClient eventServiceWebClient;
    private final WebClient paymentServiceWebClient;

    public Mono<TenantDashboardResponseDto> getTenantDashboard(Long tenantId, int year, int month) {

        // 📌 1단계: event-service에서 기준 ID 목록 확보
        Mono<List<Long>> scheduleIdsMono = fetchScheduleIdsByTenant(tenantId, year, month);

        return scheduleIdsMono.flatMap(scheduleIds -> {

            // ID 목록이 비어있으면 바로 빈 대시보드 반환
            if (scheduleIds.isEmpty()) {
                return Mono.just(createEmptyDashboard());
            }

            // 📌 2단계: 모든 상세/통계 정보 병렬 조회
            Mono<List<ScheduleDetailDto>> detailsMono = fetchScheduleDetails(scheduleIds);
            Mono<List<BookingCountDto>> countsMono = fetchBookingCounts(scheduleIds);
            Mono<TotalRevenueResponseDto> revenueMono = fetchTotalRevenue(scheduleIds);
            Mono<TotalSoldTicketsResponseDto> soldTicketsMono = fetchTotalSoldTickets(scheduleIds);
            Mono<ActiveEventCountResponseDto> activeEventsMono = fetchActiveEventCount(tenantId, year, month);

            return Mono.zip(detailsMono, countsMono, revenueMono, soldTicketsMono, activeEventsMono)
                    .map(tuple -> {
                        // 📌 3단계: 최종 데이터 조립
                        return mapToDashboardResponse(
                                tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(), tuple.getT5()
                        );
                    });
        });
    }

    private Mono<List<Long>> fetchScheduleIdsByTenant(Long tenantId, int year, int month) {
        return eventServiceWebClient.get()
                .uri("event/api/events/tenant/schedules-by-tenant?year={y}&month={m}", year, month)
                .header("x-auth-accountId", tenantId.toString())
                .retrieve()
                .bodyToMono(ScheduleIdsResponseDto.class)
                .map(ScheduleIdsResponseDto::getScheduleIds);
    }

    private Mono<List<ScheduleDetailDto>> fetchScheduleDetails(List<Long> scheduleIds) {
        return eventServiceWebClient.post()
                .uri("/event/api/events/schedules/details/_batch")
                .bodyValue(new IdsRequest(scheduleIds))
                .retrieve()
                .bodyToFlux(ScheduleDetailDto.class)
                .collectList();
    }

    private Mono<List<BookingCountDto>> fetchBookingCounts(List<Long> scheduleIds) {
        String ids = scheduleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return bookingServiceWebClient.get()
                .uri("/booking/api/bookings/counts?scheduleIds={ids}", ids)
                .retrieve()
                .bodyToFlux(BookingCountDto.class)
                .collectList();
    }

    private Mono<TotalRevenueResponseDto> fetchTotalRevenue(List<Long> scheduleIds) {
        String ids = scheduleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return paymentServiceWebClient.get()
                .uri("/payment/api/payments/summary/revenue?scheduleIds={ids}", ids)
                .retrieve()
                .bodyToMono(TotalRevenueResponseDto.class);
    }

    private Mono<TotalSoldTicketsResponseDto> fetchTotalSoldTickets(List<Long> scheduleIds) {
        String ids = scheduleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return bookingServiceWebClient.get()
                .uri("booking/api/bookings/summary/sold-count?scheduleIds={ids}", ids)
                .retrieve()
                .bodyToMono(TotalSoldTicketsResponseDto.class);
    }

    private Mono<ActiveEventCountResponseDto> fetchActiveEventCount(Long tenantId, int year, int month) {
        return eventServiceWebClient.get()
                .uri("/event/api/events/tenant/summary/count?year={y}&month={m}", year, month)
                .header("x-auth-accountId", tenantId.toString())
                .retrieve()
                .bodyToMono(ActiveEventCountResponseDto.class);
    }

    private TenantDashboardResponseDto mapToDashboardResponse(List<ScheduleDetailDto> details, List<BookingCountDto> counts,
                                                              TotalRevenueResponseDto revenue, TotalSoldTicketsResponseDto soldTickets,
                                                              ActiveEventCountResponseDto activeEvents) {

        Map<Long, Long> countsMap = counts.stream()
                .collect(Collectors.toMap(BookingCountDto::getEventScheduleId, BookingCountDto::getTicketCount));

        List<TenantDashboardResponseDto.EventItem> eventItems = details.stream()
                .map(detail -> TenantDashboardResponseDto.EventItem.builder()
                        .title(detail.getTitle())
                        .venueName(detail.getVenueName())
                        .eventDate(detail.getEventDate())
                        .ticketCount(countsMap.getOrDefault(detail.getEventScheduleId(), 0L))
                        .build())
                .collect(Collectors.toList());

        TenantDashboardResponseDto.Summary summary = TenantDashboardResponseDto.Summary.builder()
                .totalRevenue(revenue.getTotalRevenue())
                .totalTicketsSold(soldTickets.getTotalTicketsSold())
                .activeEventCount(activeEvents.getActiveEventCount())
                .build();

        return TenantDashboardResponseDto.builder()
                .data(TenantDashboardResponseDto.Data.builder()
                        .events(eventItems)
                        .summary(summary)
                        .build())
                .build();
    }

    private TenantDashboardResponseDto createEmptyDashboard() {
        return TenantDashboardResponseDto.builder()
                .data(TenantDashboardResponseDto.Data.builder()
                        .events(Collections.emptyList())
                        .summary(TenantDashboardResponseDto.Summary.builder()
                                .totalRevenue(0L)
                                .totalTicketsSold(0L)
                                .activeEventCount(0L)
                                .build())
                        .build())
                .build();
    }


}