package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.external.BookingDto;
import com.pyokemon.bff.dto.external.EventDto;
import com.pyokemon.bff.dto.external.EventScheduleDto;
import com.pyokemon.bff.dto.external.TenantDto;
import com.pyokemon.bff.dto.external.VenueDto;
import com.pyokemon.bff.dto.response.CursorPageResponse;
import com.pyokemon.bff.dto.response.UserBookingListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserBookingListService {

    private final WebClient bookingServiceWebClient; // Booking
    private final WebClient eventServiceWebClient;   // Event & Venue
    private final WebClient accountServiceWebClient; // Tenant

    @Cacheable(value = "userBookings",
            key = "T(String).format('%s:%s:%s', #accountId, (#eventGenre == null || #eventGenre.isBlank() || #eventGenre.equals(\"전체\") ? 'ALL' : #eventGenre), #cursor)")
    public Mono<CursorPageResponse<UserBookingListResponse>> getBookings(
            Long accountId, String eventGenre, Long cursor) {

        return isAll(eventGenre)
                ? getAllBookingsByAccount(accountId, cursor)
                : getBookingsByGenre(accountId, eventGenre, cursor);
    }

    // ===================== ALL 경로 =====================

    private Mono<CursorPageResponse<UserBookingListResponse>> getAllBookingsByAccount(Long accountId, Long cursor) {
        Mono<CursorPageResponse<BookingDto>> bookingPageMono = bookingServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/booking/api/bookings/accounts/{accountId}/cursor")
                        .queryParamIfPresent("cursor", Optional.ofNullable(cursor))
                        .build(accountId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<CursorPageResponse<BookingDto>>() {})
                .cache(Duration.ofSeconds(30));

        Mono<List<BookingDto>> bookingsMono = bookingPageMono
                .map(CursorPageResponse::getContent)
                .cache(Duration.ofSeconds(30));

        Mono<Map<Long, EventScheduleDto>> esByIdMono = bookingsMono
                .flatMap(this::batchSchedulesFromBookings)
                .cache(Duration.ofSeconds(30));

        Mono<Map<Long, TenantDto>> tenantsByIdMono = bookingsMono
                .flatMap(this::batchTenantsFromBookings)
                .cache(Duration.ofSeconds(30));

        Mono<Map<Long, VenueDto>> venuesByIdMono = esByIdMono
                .flatMap(this::batchVenuesFromSchedules)
                .cache(Duration.ofSeconds(30));

        Mono<Map<Long, EventDto>> eventsByIdMono = esByIdMono
                .flatMap(this::batchEventsFromSchedules)
                .cache(Duration.ofSeconds(30));

        Mono<List<UserBookingListResponse>> itemsMono =
                mergeAsBookingOrder(bookingsMono, esByIdMono, tenantsByIdMono, venuesByIdMono, eventsByIdMono);

        return pack(bookingPageMono, itemsMono);
    }

    // ===================== 장르 경로 =====================

    private Mono<CursorPageResponse<UserBookingListResponse>> getBookingsByGenre(Long accountId, String eventGenre, Long cursor) {
        Mono<List<Long>> eventIdsMono = fetchEventIdsByGenre(eventGenre).cache(Duration.ofSeconds(60));
        Mono<List<Long>> scheduleIdsMono = eventIdsMono.flatMap(this::fetchScheduleIdsByEventIds).cache(Duration.ofSeconds(60));

        Mono<CursorPageResponse<BookingDto>> bookingPageMono = scheduleIdsMono.flatMap(scheduleIds -> {
            if (scheduleIds.isEmpty()) return Mono.just(emptyPage());
            return bookingServiceWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/booking/api/bookings/accounts/{accountId}/by-schedules/cursor")
                            .queryParamIfPresent("cursor", Optional.ofNullable(cursor))
                            .build(accountId))
                    .bodyValue(Map.of("ids", scheduleIds))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<CursorPageResponse<BookingDto>>() {});
        }).cache(Duration.ofSeconds(30));

        Mono<List<BookingDto>> bookingsMono = bookingPageMono
                .map(CursorPageResponse::getContent)
                .cache(Duration.ofSeconds(30));

        Mono<Map<Long, EventScheduleDto>> esByIdMono = bookingsMono
                .flatMap(this::batchSchedulesFromBookings)
                .cache(Duration.ofSeconds(30));

        Mono<Map<Long, TenantDto>> tenantsByIdMono = bookingsMono
                .flatMap(this::batchTenantsFromBookings)
                .cache(Duration.ofSeconds(30));

        Mono<Map<Long, VenueDto>> venuesByIdMono = esByIdMono
                .flatMap(this::batchVenuesFromSchedules)
                .cache(Duration.ofSeconds(30));

        Mono<Map<Long, EventDto>> eventsByIdMono = esByIdMono
                .flatMap(this::batchEventsFromSchedules)
                .cache(Duration.ofSeconds(30));

        Mono<List<UserBookingListResponse>> itemsMono =
                mergeAsBookingOrder(bookingsMono, esByIdMono, tenantsByIdMono, venuesByIdMono, eventsByIdMono);

        return pack(bookingPageMono, itemsMono);
    }

    // ===================== Merge & Pack =====================

    private Mono<List<UserBookingListResponse>> mergeAsBookingOrder(
            Mono<List<BookingDto>> bookingsMono,
            Mono<Map<Long, EventScheduleDto>> esByIdMono,
            Mono<Map<Long, TenantDto>> tenantsByIdMono,
            Mono<Map<Long, VenueDto>> venuesByIdMono,
            Mono<Map<Long, EventDto>> eventsByIdMono) {

        return Mono.zip(bookingsMono, esByIdMono, tenantsByIdMono, venuesByIdMono, eventsByIdMono)
                .map(t -> {
                    List<BookingDto> bookings = t.getT1();
                    Map<Long, EventScheduleDto> esMap = t.getT2();
                    Map<Long, TenantDto> tnMap = t.getT3();
                    Map<Long, VenueDto> vnMap = t.getT4();
                    Map<Long, EventDto> evMap = t.getT5();

                    List<UserBookingListResponse> items = new ArrayList<>(bookings.size());
                    for (BookingDto b : bookings) {
                        EventScheduleDto es = esMap.get(b.getEventScheduleId());
                        if (es == null) continue;

                        TenantDto tn = Optional.ofNullable(b.getTenantId()).map(tnMap::get).orElse(null);
                        VenueDto vn = vnMap.get(es.getVenueId());
                        EventDto ev = evMap.get(es.getEventId());

                        items.add(UserBookingListResponse.builder()
                                .bookingId(b.getBookingId())
                                .eventTitle(ev != null ? ev.getTitle() : null)
                                .eventDate(es.getEventDate())
                                .venueName(vn != null ? vn.getVenueName() : null)
                                .tenantName(tn != null ? tn.getName() : null)
                                .thumbnailUrl(ev != null ? ev.getThumbnailUrl() : null)
                                .build());
                    }
                    return items;
                });
    }

    private Mono<CursorPageResponse<UserBookingListResponse>> pack(
            Mono<CursorPageResponse<BookingDto>> bookingPageMono,
            Mono<List<UserBookingListResponse>> itemsMono) {

        return Mono.zip(bookingPageMono, itemsMono)
                .map(t -> {
                    CursorPageResponse<BookingDto> src = t.getT1();
                    List<UserBookingListResponse> items = t.getT2();

                    CursorPageResponse<UserBookingListResponse> out = new CursorPageResponse<>();
                    out.setContent(items);
                    out.setNextCursor(src.getNextCursor());
                    out.setHasMore(src.getHasMore());
                    return out;
                })
                .onErrorResume(ex -> {
                    CursorPageResponse<UserBookingListResponse> empty = new CursorPageResponse<>();
                    empty.setContent(List.of());
                    empty.setNextCursor(null);
                    empty.setHasMore(false);
                    return Mono.just(empty);
                });
    }

    // ===================== Batch helpers =====================

    private Mono<Map<Long, EventScheduleDto>> batchSchedulesFromBookings(List<BookingDto> bookings) {
        List<Long> esIds = bookings.stream()
                .map(BookingDto::getEventScheduleId)
                .filter(Objects::nonNull).distinct().toList();
        if (esIds.isEmpty()) return Mono.just(Map.of());
        return eventServiceWebClient.post().uri("/event/api/event-schedules/_batch")
                .bodyValue(Map.of("ids", esIds))
                .retrieve()
                .bodyToFlux(EventScheduleDto.class)
                .collectMap(EventScheduleDto::getEventScheduleId, it -> it);
    }

    private Mono<Map<Long, TenantDto>> batchTenantsFromBookings(List<BookingDto> bookings) {
        List<Long> tenantIds = bookings.stream()
                .map(BookingDto::getTenantId)
                .filter(Objects::nonNull).distinct().toList();
        if (tenantIds.isEmpty()) return Mono.just(Map.of());
        return accountServiceWebClient.post().uri("account/api/bff/tenants/_batch")
                .bodyValue(Map.of("ids", tenantIds))
                .retrieve()
                .bodyToFlux(TenantDto.class)
                .collectMap(TenantDto::getTenantId, it -> it);
    }

    private Mono<Map<Long, VenueDto>> batchVenuesFromSchedules(Map<Long, EventScheduleDto> esMap) {
        List<Long> venueIds = esMap.values().stream()
                .map(EventScheduleDto::getVenueId)
                .filter(Objects::nonNull).distinct().toList();
        if (venueIds.isEmpty()) return Mono.just(Map.of());
        return eventServiceWebClient.post().uri("/event/api/venues/_batch")
                .bodyValue(Map.of("ids", venueIds))
                .retrieve()
                .bodyToFlux(VenueDto.class)
                .collectMap(VenueDto::getVenueId, it -> it);
    }

    private Mono<Map<Long, EventDto>> batchEventsFromSchedules(Map<Long, EventScheduleDto> esMap) {
        List<Long> eventIds = esMap.values().stream()
                .map(EventScheduleDto::getEventId)
                .filter(Objects::nonNull).distinct().toList();
        if (eventIds.isEmpty()) return Mono.just(Map.of());
        return eventServiceWebClient.post().uri("/event/api/bff/events/_batch")
                .bodyValue(Map.of("ids", eventIds))
                .retrieve()
                .bodyToFlux(EventDto.class)
                .collectMap(EventDto::getEventId, it -> it);
    }

    // ===================== Common helpers =====================

    private boolean isAll(String genre) {
        return genre == null || genre.isBlank() || "전체".equals(genre);
    }

    private Mono<List<Long>> fetchEventIdsByGenre(String genre) {
        return eventServiceWebClient.post()
                .uri("/event/api/events/_ids-by-genre")
                .bodyValue(Map.of("genre", genre))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Long>>() {});
    }

    private Mono<List<Long>> fetchScheduleIdsByEventIds(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return Mono.just(List.of());
        return eventServiceWebClient.post()
                .uri("/event/api/event-schedules/_ids-by-events")
                .bodyValue(Map.of("ids", eventIds)) // 소문자 ids 주의
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Long>>() {});
    }

    private static CursorPageResponse<BookingDto> emptyPage() {
        CursorPageResponse<BookingDto> p = new CursorPageResponse<>();
        p.setContent(List.of());
        p.setNextCursor(null);
        p.setHasMore(false);
        return p;
    }
}
