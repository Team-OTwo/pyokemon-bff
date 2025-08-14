package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.response.BookingDetailResponse;
import com.pyokemon.bff.dto.external.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class BookingDetailService {
    private final WebClient bookingClient;
    private final WebClient eventClient;
    private final WebClient accountClient;
    private final WebClient paymentClient;

    public BookingDetailService(WebClient bookingServiceWebClient, WebClient eventServiceWebClient,
                                WebClient accountServiceWebClient, WebClient paymentServiceWebClient) {
        this.bookingClient = bookingServiceWebClient;
        this.eventClient = eventServiceWebClient;
        this.accountClient = accountServiceWebClient;
        this.paymentClient = paymentServiceWebClient;
    }

    public Mono<BookingDetailResponse> getBookingDetail(Long bookingId, Long accountId) {
        // 1단계: booking-service와 account-service 호출
        Mono<BookingDto> bookingMono = bookingClient.get()
                .uri("/booking/api/bookings/{id}", bookingId)
                .retrieve()
                .bodyToMono(BookingDto.class)
                .filter(booking -> booking.getAccountId().equals(accountId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Booking not found or unauthorized")));

        Mono<AccountDto> accountMono = accountClient.get()
                .uri("/account/api/accounts/{id}", accountId)
                .retrieve()
                .bodyToMono(AccountDto.class);

        return Mono.zip(bookingMono, accountMono)
                .flatMap(tuple -> {
                    BookingDto booking = tuple.getT1();
                    AccountDto account = tuple.getT2();

                    // 2단계: event-service (schedule, seat), payment-service 호출
                    Mono<EventScheduleDto> scheduleMono = eventClient.get()
                            .uri("event/api/schedules/{id}", booking.getEventScheduleId())
                            .retrieve()
                            .bodyToMono(EventScheduleDto.class);

                    Mono<PaymentDto> paymentMono = paymentClient.get()
                            .uri("event/api/payments/{id}", booking.getPaymentId())
                            .retrieve()
                            .bodyToMono(PaymentDto.class);

                    Mono<SeatDto> seatMono = eventClient.get()
                            .uri("event/api/seats/{id}", booking.getSeatId())
                            .retrieve()
                            .bodyToMono(SeatDto.class);

                    return Mono.zip(scheduleMono, paymentMono, seatMono)
                            .flatMap(t -> {
                                EventScheduleDto schedule = t.getT1();
                                PaymentDto payment = t.getT2();
                                SeatDto seat = t.getT3();

                                // 3단계: event-service (event, venue, seat class) 호출
                                Mono<EventDto> eventMono = eventClient.get()
                                        .uri("event/api/events/{id}", schedule.getEventId())
                                        .retrieve()
                                        .bodyToMono(EventDto.class);

                                Mono<VenueDto> venueMono = eventClient.get()
                                        .uri("event/api/venues/{id}", schedule.getVenueId())
                                        .retrieve()
                                        .bodyToMono(VenueDto.class);

                                Mono<SeatClassDto> seatClassMono = eventClient.get()
                                        .uri("event/api/seat-classes/{id}", seat.getSeatClassId())
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
                                            eventInfo.setEventDate(schedule.getEventDate().toString());
                                            BookingDetailResponse.EventInfo.VenueInfo venueInfo = new BookingDetailResponse.EventInfo.VenueInfo();
                                            venueInfo.setName(t2.getT2().getName());
                                            eventInfo.setVenue(venueInfo);
                                            response.setEvent(eventInfo);

                                            BookingDetailResponse.SeatInfo seatInfo = new BookingDetailResponse.SeatInfo();
                                            seatInfo.setClassName(t2.getT3().getClassName());
                                            seatInfo.setFloor(seat.getFloor().intValue());
                                            seatInfo.setRow(seat.getRow());
                                            seatInfo.setCol(seat.getCol());
                                            response.setSeat(seatInfo);

                                            BookingDetailResponse.PaymentInfo paymentInfo = new BookingDetailResponse.PaymentInfo();
                                            paymentInfo.setMethod(payment.getMethod());
                                            paymentInfo.setStatus(payment.getStatus());
                                            paymentInfo.setPaidAt(payment.getUpdatedAt().toString());
                                            paymentInfo.setAmount(payment.getAmount());
//                                            response.setPayment(paymentInfo);

                                            return response;
                                        });
                            });
                });
    }
}