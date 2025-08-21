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
        // 1단계: account_id 기준 tb_booking 조회
        Mono<PageResponse<BookingDto>> bookingsMono = bookingServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/booking/api/bookings/accounts/{accountId}/bookings/order")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(accountId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<PageResponse<BookingDto>>() {
                });


//         2단계: 공연/결제 병렬 조회 및 3단계: 공연/공연장 병렬 조회
        return bookingsMono.flatMap(pageResponse -> {
            List<BookingDto> bookingList = pageResponse.getContent();

            Flux<MyPageBookingResponse> responses = Flux.fromIterable(bookingList)

                    .flatMapSequential(booking -> {
                        Mono<PaymentDto> paymentMono = getPayment(booking.getPaymentId());

                        Mono<EventScheduleDto> eventScheduleMono = getEventSchedule(booking.getEventScheduleId());

                        return Mono.zip(paymentMono, eventScheduleMono)
                                .flatMap(tuple -> {
                                    PaymentDto payment = tuple.getT1();
                                    EventScheduleDto eventSchedule = tuple.getT2();

                                    Mono<EventDto> eventMono = getEvent(eventSchedule.getEventId());
                                    Mono<VenueDto> venueMono = getVenue(eventSchedule.getVenueId());

                                    return Mono.zip(eventMono, venueMono)
                                            .map(innerTuple -> {
                                                EventDto event = innerTuple.getT1();
                                                VenueDto venue = innerTuple.getT2();

                                                return MyPageBookingResponse.builder()
                                                        .bookingId(booking.getBookingId())
                                                        .eventTitle(event.getTitle())
                                                        .eventDate(formatEventDate(eventSchedule.getEventDate().format(DATE_FORMATTER)))
                                                        .venueName(venue.getVenueName())
                                                        .thumbnailUrl(event.getThumbnailUrl())
                                                        .totalPrice(payment.getAmount())
                                                        .status(booking.getStatus().getDisplayValue())
                                                        .build();
                                            });
                                });
                    });
            return responses.collectList()
                    .map(myPageResponses -> new PageResponse<>(
                            myPageResponses,
                            pageResponse.getPage(),
                            pageResponse.getTotalCount()
                    ));
        });
    }


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
}