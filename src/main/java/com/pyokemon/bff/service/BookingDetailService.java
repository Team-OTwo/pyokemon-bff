package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.external.AccountDto;
import com.pyokemon.bff.dto.external.BookingDto;
import com.pyokemon.bff.dto.external.EventDto;
import com.pyokemon.bff.dto.external.EventScheduleDto;
import com.pyokemon.bff.dto.external.PaymentDto;
import com.pyokemon.bff.dto.external.SeatClassDto;
import com.pyokemon.bff.dto.external.SeatDto;
import com.pyokemon.bff.dto.external.VenueDto;
import com.pyokemon.bff.dto.response.BookingDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class BookingDetailService {

    private final WebClient bookingServiceWebClient;
    private final WebClient eventServiceWebClient;
    private final WebClient accountServiceWebClient;
    private final WebClient paymentServiceWebClient;

    /**
     * 예매 상세 정보 조회
     * @param bookingId 예매 ID
     * @param accountId 사용자 ID
     * @return 예매 상세 정보
     */
    @Cacheable(value = "bookingDetails", key = "#bookingId + '_' + #accountId")
    public Mono<BookingDetailResponse> getBookingDetail(Long bookingId, Long accountId) {
        // 1단계: 예매 + 사용자 동시 조회
        Mono<BookingDto> bookingMono = bookingServiceWebClient.get()
                .uri("/api/v1/bookings/{id}", bookingId)
                .retrieve()
                .bodyToMono(BookingDto.class);
        
        Mono<AccountDto> accountMono = getAccount(accountId);
        
        // 2단계: 일정/결제/좌석 병렬 조회
        return Mono.zip(bookingMono, accountMono)
                .flatMap(tuple -> {
                    BookingDto booking = tuple.getT1();
                    AccountDto account = tuple.getT2();
                    
                    // 권한 체크: 예매한 사용자와 조회 요청한 사용자가 일치하는지 확인
                    if (!booking.getAccountId().equals(accountId)) {
                        return Mono.error(new RuntimeException("권한이 없습니다."));
                    }
                    
                    Mono<EventScheduleDto> eventScheduleMono = getEventSchedule(booking.getEventScheduleId());
                    Mono<PaymentDto> paymentMono = getPayment(booking.getPaymentId());
                    Mono<SeatDto> seatMono = getSeat(booking.getSeatId());
                    
                    return Mono.zip(eventScheduleMono, paymentMono, seatMono)
                            .flatMap(innerTuple -> {
                                EventScheduleDto eventSchedule = innerTuple.getT1();
                                PaymentDto payment = innerTuple.getT2();
                                SeatDto seat = innerTuple.getT3();
                                
                                // 3단계: 공연/공연장/좌석등급 병렬 조회
                                Mono<EventDto> eventMono = getEvent(eventSchedule.getEventId());
                                Mono<VenueDto> venueMono = getVenue(eventSchedule.getVenueId());
                                Mono<SeatClassDto> seatClassMono = getSeatClass(seat.getSeatClassId());
                                
                                return Mono.zip(eventMono, venueMono, seatClassMono)
                                        .map(finalTuple -> {
                                            EventDto event = finalTuple.getT1();
                                            VenueDto venue = finalTuple.getT2();
                                            SeatClassDto seatClass = finalTuple.getT3();
                                            
                                            // 응답 객체 생성
                                            BookingDetailResponse.UserInfo userInfo = BookingDetailResponse.UserInfo.builder()
                                                    .name(account.getName())
                                                    .build();
                                            
                                            BookingDetailResponse.EventInfo.VenueInfo venueInfo = BookingDetailResponse.EventInfo.VenueInfo.builder()
                                                    .name(venue.getName())
                                                    .build();
                                            
                                            BookingDetailResponse.EventInfo eventInfo = BookingDetailResponse.EventInfo.builder()
                                                    .title(event.getTitle())
                                                    .thumbnailUrl(event.getThumbnailUrl())
                                                    .eventDate(eventSchedule.getEventDate().toLocalDate().toString())
                                                    .venue(venueInfo)
                                                    .build();
                                            
                                            BookingDetailResponse.SeatInfo seatInfo = BookingDetailResponse.SeatInfo.builder()
                                                    .className(seatClass.getClassName())
                                                    .floor(seat.getFloor())
                                                    .row(seat.getRow())
                                                    .col(seat.getCol())
                                                    .build();
                                            
                                            BookingDetailResponse.PaymentInfo paymentInfo = BookingDetailResponse.PaymentInfo.builder()
                                                    .method(payment.getMethod())
                                                    .status(payment.getStatus())
                                                    .paidAt(payment.getPaidAt())
                                                    .amount(payment.getAmount())
                                                    .build();
                                            
                                            return BookingDetailResponse.builder()
                                                    .bookingId(booking.getId())
                                                    .status(booking.getStatus())
                                                    .createdAt(booking.getCreatedAt())
                                                    .user(userInfo)
                                                    .event(eventInfo)
                                                    .seat(seatInfo)
                                                    .payment(paymentInfo)
                                                    .build();
                                        });
                            });
                });
    }

    private Mono<AccountDto> getAccount(Long accountId) {
        return accountServiceWebClient.get()
                .uri("/api/v1/accounts/{id}", accountId)
                .retrieve()
                .bodyToMono(AccountDto.class);
    }

    private Mono<EventScheduleDto> getEventSchedule(Long eventScheduleId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/event-schedules/{id}", eventScheduleId)
                .retrieve()
                .bodyToMono(EventScheduleDto.class);
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
}