package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.PaymentStatus;
import com.pyokemon.bff.dto.external.BookingDto;
import com.pyokemon.bff.dto.external.EventDto;
import com.pyokemon.bff.dto.external.EventScheduleDto;
import com.pyokemon.bff.dto.external.PaymentDto;
import com.pyokemon.bff.dto.external.VenueDto;
import com.pyokemon.bff.dto.response.MyPageBookingResponse;
import com.pyokemon.bff.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MyPageBookingService {

    private final WebClient bookingServiceWebClient;
    private final WebClient eventServiceWebClient;
    private final WebClient paymentServiceWebClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd(E) HH:mm");

    /**
     * 사용자 마이페이지 예매 내역 조회
     *
     * @param accountId 사용자 ID
     * @return 예매 목록
     */
    @Cacheable(value = "myBookings", key = "#accountId")
    public Mono<PageResponse<MyPageBookingResponse>> getMyBookings(Long accountId, Integer page, Integer size) {
        // 1단계: Booking 조회
        Mono<PageResponse<BookingDto>> bookingsMono = bookingServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/booking/api/bookings/accounts/{accountId}/bookings/order")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(accountId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<PageResponse<BookingDto>>() {
                });


        return bookingsMono.flatMap(pageResponse -> {
            List<BookingDto> bookingList = pageResponse.getContent();

            // 2단계: ID 모으기
            List<Long> paymentIds = bookingList.stream().map(BookingDto::getPaymentId).filter(Objects::nonNull).distinct().toList();
            List<Long> scheduleIds = bookingList.stream()
                    .map(BookingDto::getEventScheduleId)
                    .distinct().toList();

            // 3단계: batch API 호출
            Mono<Map<Long, PaymentDto>> paymentsMono = getPaymentsBatch(paymentIds);
            Mono<Map<Long, EventScheduleDto>> schedulesMono = getEventSchedulesBatch(scheduleIds);

            // 4단계: Event, Venue ID 모으고 batch 조회
            Mono<Map<Long, EventDto>> eventsMono = schedulesMono.flatMap(schedules -> {
                List<Long> eventIds = schedules.values().stream()
                        .map(EventScheduleDto::getEventId)
                        .distinct().toList();
                return getEventsBatch(eventIds);
            });

            Mono<Map<Long, VenueDto>> venuesMono = schedulesMono.flatMap(schedules -> {
                List<Long> venueIds = schedules.values().stream()
                        .map(EventScheduleDto::getVenueId)
                        .distinct().toList();
                return getVenuesBatch(venueIds);
            });


            return Mono.zip(paymentsMono, schedulesMono, eventsMono, venuesMono)
                    .map(tuple -> {
                        Map<Long, PaymentDto> payments = tuple.getT1();
                        Map<Long, EventScheduleDto> schedules = tuple.getT2();
                        Map<Long, EventDto> events = tuple.getT3();
                        Map<Long, VenueDto> venues = tuple.getT4();

                        List<MyPageBookingResponse> responses = bookingList.stream()
                                .map(booking -> {
                                    PaymentDto payment = payments.get(booking.getPaymentId());
                                    EventScheduleDto schedule = schedules.get(booking.getEventScheduleId());
                                    EventDto event = schedule != null ? events.get(schedule.getEventId()) : null;
                                    VenueDto venue = schedule != null ? venues.get(schedule.getVenueId()) : null;

                                    return MyPageBookingResponse.builder()
                                            .bookingId(booking.getBookingId())
                                            .eventTitle(event != null ? event.getTitle() : null)
                                            .eventDate(schedule != null ?
                                                    schedule.getEventDate().format(DATE_FORMATTER) : null)
                                            .venueName(venue != null ? venue.getVenueName() : null)
                                            .thumbnailUrl(event != null ? event.getThumbnailUrl() : null)
                                            .totalPrice(payment != null ? payment.getAmount() : null)
                                            .status(translateStatus(booking.getStatus().getDisplayValue()))
                                            .build();
                                }).toList();

                        return new PageResponse<>(responses, pageResponse.getPage(), pageResponse.getTotalCount());
                    });
        });
    }


    // 내부 호출
    private Mono<PaymentDto> getPayment(Long paymentId) {
        return paymentServiceWebClient.get()
                .uri("/payment/api/payments/{id}", paymentId)
                .retrieve()
                .bodyToMono(PaymentDto.class);
    }

    private Mono<EventScheduleDto> getEventSchedule(Long eventScheduleId) {
        return eventServiceWebClient.get()
                .uri("/event/api/event-schedules/{id}", eventScheduleId)
                .retrieve()
                .bodyToMono(EventScheduleDto.class);
    }

    private Mono<EventDto> getEvent(Long eventId) {
        return eventServiceWebClient.get()
                .uri("/event/api/bff/events/{id}", eventId)
                .retrieve()
                .bodyToMono(EventDto.class);
    }

    private Mono<VenueDto> getVenue(Long venueId) {
        return eventServiceWebClient.get()
                .uri("/event/api/venues/{id}", venueId)
                .retrieve()
                .bodyToMono(VenueDto.class);
    }

    /**
     * 이벤트 날짜 포맷팅
     */
    private String formatEventDate(String eventDate) {
        return eventDate;
    }

    /**
     * 상태값 번역
     *
     * @param status 원본 상태값 (BOOKED, PAID, CANCELLED)
     * @return 번역된 상태값 (예매 완료, 결제 완료, 취소됨)
     */
    private String translateStatus(String status) {
        if (status == null) {
            return "";
        }

        return switch (status.toUpperCase()) {
            case "BOOKED" -> "예매 완료";
            case "PAID" -> "결제 완료";
            case "CANCELLED" -> "취소됨";
            default -> status;
        };
    }


    // Batch 호출 헬퍼
    private Mono<Map<Long, PaymentDto>> getPaymentsBatch(List<Long> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return paymentServiceWebClient.post().uri("/payment/api/payments/_batch")
                .bodyValue(Map.of("ids", ids))
                .retrieve().bodyToFlux(PaymentDto.class)
                .collectMap(PaymentDto::getPaymentId, p -> p);
    }

    private Mono<Map<Long, EventScheduleDto>> getEventSchedulesBatch(List<Long> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return eventServiceWebClient.post()
                .uri("/event/api/event-schedules/_batch")
                .bodyValue(Map.of("ids", ids))
                .retrieve()
                .bodyToFlux(EventScheduleDto.class)
                .collectMap(EventScheduleDto::getEventScheduleId, e -> e);
    }

    private Mono<Map<Long, EventDto>> getEventsBatch(List<Long> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return eventServiceWebClient.post()
                .uri("/event/api/bff/events/_batch")
                .bodyValue(Map.of("ids", ids))
                .retrieve()
                .bodyToFlux(EventDto.class)
                .collectMap(EventDto::getEventId, e -> e);
    }

    private Mono<Map<Long, VenueDto>> getVenuesBatch(List<Long> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return eventServiceWebClient.post()
                .uri("/event/api/venues/_batch")
                .bodyValue(Map.of("ids", ids))
                .retrieve()
                .bodyToFlux(VenueDto.class)
                .collectMap(VenueDto::getVenueId, v -> v);
    }
}