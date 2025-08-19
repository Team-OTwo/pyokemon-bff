package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.response.BookingDetailResponse;
import com.pyokemon.bff.dto.external.*;
import lombok.RequiredArgsConstructor;
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

    public Mono<BookingDetailResponse> getBookingDetail(Long bookingId, Long accountId) {
        // 1단계: booking-service와 account-service 호출
        Mono<BookingDto> bookingMono = bookingServiceWebClient.get()
                .uri("/booking/api/bookings/bff/{bookingId}", bookingId)
                .retrieve()
                .bodyToMono(BookingDto.class)
                .filter(booking -> booking.getAccountId().equals(accountId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Booking not found or unauthorized")));

        Mono<AccountDto> accountMono = accountServiceWebClient.get()
                .uri("/account/api/users/{accountId}", accountId)
                .retrieve()
                .bodyToMono(AccountDto.class);

        return Mono.zip(bookingMono, accountMono)
                .flatMap(tuple -> {
                    BookingDto booking = tuple.getT1();
                    AccountDto account = tuple.getT2();

                    // 2단계: event-service (schedule, seat), payment-service 호출
                    Mono<EventScheduleDto> scheduleMono = eventServiceWebClient.get()
                            .uri("/event/api/event-schedules/{eventScheduleId}", booking.getEventScheduleId())
                            .retrieve()
                            .bodyToMono(EventScheduleDto.class);

                    Mono<PaymentDto> paymentMono = paymentServiceWebClient.get()
                            .uri("/payment/api/payments/{paymentId}", booking.getPaymentId())
                            .retrieve()
                            .bodyToMono(PaymentDto.class);

                    Mono<SeatDto> seatMono = eventServiceWebClient.get()
                            .uri("/event/api/seats/{seatId}", booking.getSeatId())
                            .retrieve()
                            .bodyToMono(SeatDto.class);

                    return Mono.zip(scheduleMono, paymentMono, seatMono)
                            .flatMap(t -> {
                                EventScheduleDto schedule = t.getT1();
                                PaymentDto payment = t.getT2();
                                SeatDto seat = t.getT3();

                                // 3단계: event-service (event, venue, seat class) 호출
                                Mono<EventDto> eventMono = eventServiceWebClient.get()
                                        .uri("/event/api/bff/events/{eventId}", schedule.getEventId())
                                        .retrieve()
                                        .bodyToMono(EventDto.class);

                                Mono<VenueDto> venueMono = eventServiceWebClient.get()
                                        .uri("/event/api/venues/{venueId}", schedule.getVenueId())
                                        .retrieve()
                                        .bodyToMono(VenueDto.class);

                                Mono<SeatClassDto> seatClassMono = eventServiceWebClient.get()
                                        .uri("/event/api/seat-classes/{seatClassId}", seat.getSeatClassId())
                                        .retrieve()
                                        .bodyToMono(SeatClassDto.class);

                                return Mono.zip(eventMono, venueMono, seatClassMono)
                                        .map(t2 -> {
                                            BookingDetailResponse response = new BookingDetailResponse();
                                            response.setBookingId(booking.getId());
                                            response.setStatus(booking.getStatus().getDisplayValue());
                                            response.setCreatedAt(booking.getUpdatedAt());

                                            BookingDetailResponse.UserInfo user = new BookingDetailResponse.UserInfo();
                                            user.setName(account.getName());
                                            response.setUser(user);

                                            BookingDetailResponse.EventInfo eventInfo = new BookingDetailResponse.EventInfo();
                                            eventInfo.setTitle(t2.getT1().getTitle());
                                            eventInfo.setThumbnailUrl(t2.getT1().getThumbnailUrl());
                                            eventInfo.setEventDate(schedule.getEventDate().toLocalDate().toString());
                                            BookingDetailResponse.EventInfo.VenueInfo venueInfo = new BookingDetailResponse.EventInfo.VenueInfo();
                                            venueInfo.setName(t2.getT2().getName());
                                            eventInfo.setVenue(venueInfo);
                                            response.setEvent(eventInfo);

                                            BookingDetailResponse.SeatInfo seatInfo = new BookingDetailResponse.SeatInfo();
                                            seatInfo.setClassName(t2.getT3().getClassName());
                                            seatInfo.setFloor(seat.getFloor());
                                            seatInfo.setRow(seat.getRow());
                                            seatInfo.setCol(seat.getCol());
                                            response.setSeat(seatInfo);

                                            BookingDetailResponse.PaymentInfo paymentInfo = new BookingDetailResponse.PaymentInfo();
                                            paymentInfo.setMethod(payment.getMethod());
                                            paymentInfo.setStatus(payment.getStatus().getDisplayValue());
                                            paymentInfo.setPaidAt(payment.getUpdatedAt().toString());
                                            paymentInfo.setAmount(payment.getAmount());
                                            response.setPayment(paymentInfo);

                                            return response;
                                        });
                            });
                });
    }
}