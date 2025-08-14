package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.external.AccountDto;
import com.pyokemon.bff.dto.external.BookingDto;
import com.pyokemon.bff.dto.external.EventDto;
import com.pyokemon.bff.dto.external.EventScheduleDto;
import com.pyokemon.bff.dto.external.PaymentDto;
import com.pyokemon.bff.dto.external.SeatClassDto;
import com.pyokemon.bff.dto.external.SeatDto;
import com.pyokemon.bff.dto.external.VenueDto;
import com.pyokemon.bff.dto.response.BookingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final WebClient bookingServiceWebClient;
    private final WebClient eventServiceWebClient;
    private final WebClient accountServiceWebClient;
    private final WebClient paymentServiceWebClient;

    /**
     * 테넌트 예매 현황 조회
     * @param accountId 테넌트 ID
     * @param eventScheduleId 공연 일정 ID
     * @return 예매 목록
     */
    @Cacheable(value = "bookings", key = "#eventScheduleId + '_' + #accountId")
    public Flux<BookingResponse> getBookings(Long accountId, Long eventScheduleId) {
        // 1단계: event_schedule_id 기준 동시 조회
        Mono<EventScheduleDto> eventScheduleMono = getEventSchedule(eventScheduleId);
        
        Flux<BookingDto> bookingsFlux = bookingServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/bookings")
                        .queryParam("event_schedule_id", eventScheduleId)
                        .build())
                .retrieve()
                .bodyToFlux(BookingDto.class);
        
        // 2단계: booking row 하나당 병렬 조회 및 3단계: 좌석 등급명 조회
        return bookingsFlux.flatMap(booking -> {
            Mono<AccountDto> accountMono = getAccount(booking.getAccountId());
            Mono<PaymentDto> paymentMono = getPayment(booking.getPaymentId());
            Mono<SeatDto> seatMono = getSeat(booking.getSeatId());
            
            return eventScheduleMono.flatMap(eventSchedule -> {
                Mono<EventDto> eventMono = getEvent(eventSchedule.getEventId());
                Mono<VenueDto> venueMono = getVenue(eventSchedule.getVenueId());
                
                return Mono.zip(accountMono, paymentMono, seatMono, eventMono, venueMono)
                        .flatMap(tuple -> {
                            AccountDto account = tuple.getT1();
                            PaymentDto payment = tuple.getT2();
                            SeatDto seat = tuple.getT3();
                            EventDto event = tuple.getT4();
                            VenueDto venue = tuple.getT5();
                            
                            return getSeatClass(seat.getSeatClassId()).map(seatClass -> {
                                BookingResponse.SeatInfo seatInfo = BookingResponse.SeatInfo.builder()
                                        .className(seatClass.getClassName())
                                        .floor(seat.getFloor())
                                        .row(seat.getRow())
                                        .col(seat.getCol())
                                        .build();
                                
                                return BookingResponse.builder()
                                        .bookingId(booking.getId())
                                        .userName(account.getName())
                                        .eventTitle(event.getTitle())
                                        .eventDate(eventSchedule.getEventDate().toLocalDate().toString())
                                        .venueName(venue.getName())
                                        .seat(seatInfo)
                                        .thumbnailUrl(event.getThumbnailUrl())
                                        .totalPrice(payment.getAmount())
//                                        .status(translateStatus(booking.getStatus()))
                                        .build();
                            });
                        });
            });
        });
    }

    private Mono<EventScheduleDto> getEventSchedule(Long eventScheduleId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/event-schedules/{id}", eventScheduleId)
                .retrieve()
                .bodyToMono(EventScheduleDto.class);
    }

    private Mono<AccountDto> getAccount(Long accountId) {
        return accountServiceWebClient.get()
                .uri("/api/v1/accounts/{id}", accountId)
                .retrieve()
                .bodyToMono(AccountDto.class);
    }

    private Mono<PaymentDto> getPayment(Long paymentId) {
        return paymentServiceWebClient.get()
                .uri("/api/v1/payments/{id}", paymentId)
                .retrieve()
                .bodyToMono(PaymentDto.class);
    }

    private Mono<SeatDto> getSeat(Long seatId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/seats/{id}", seatId)
                .retrieve()
                .bodyToMono(SeatDto.class);
    }

    private Mono<EventDto> getEvent(Long eventId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/events/{id}", eventId)
                .retrieve()
                .bodyToMono(EventDto.class);
    }

    private Mono<VenueDto> getVenue(Long venueId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/venues/{id}", venueId)
                .retrieve()
                .bodyToMono(VenueDto.class);
    }

    private Mono<SeatClassDto> getSeatClass(Long seatClassId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/seat-classes/{id}", seatClassId)
                .retrieve()
                .bodyToMono(SeatClassDto.class);
    }
    
    /**
     * 상태값 번역
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