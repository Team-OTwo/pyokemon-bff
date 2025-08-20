package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.response.BookingDetailResponse;
import com.pyokemon.bff.dto.external.*;
import com.pyokemon.bff.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingDetailService {

    private final WebClient bookingServiceWebClient;
    private final WebClient eventServiceWebClient;
    private final WebClient accountServiceWebClient;
    private final WebClient paymentServiceWebClient;

    public Mono<BookingDetailResponse> getBookingDetail(Long bookingId, Long accountId) {
        log.info("Booking detail request received for bookingId: {} and accountId: {}", bookingId, accountId);

        // 1단계: booking-service와 account-service 호출 (병렬)
        Mono<BookingDto> bookingMono = fetchBooking(bookingId, accountId);
        Mono<AccountDto> accountMono = fetchAccount(accountId);

        return Mono.zip(bookingMono, accountMono)
                .flatMap(tuple -> {
                    BookingDto booking = tuple.getT1();
                    AccountDto account = tuple.getT2();

                    // 2단계: event-service (schedule, seat) 및 payment-service 호출 (병렬)
                    Mono<EventScheduleDto> scheduleMono = fetchEventSchedule(booking.getEventScheduleId());
                    Mono<PaymentDto> paymentMono = fetchPayment(booking.getPaymentId());
                    Mono<SeatDto> seatMono = fetchSeat(booking.getSeatId());

                    return Mono.zip(scheduleMono, paymentMono, seatMono)
                            .flatMap(t -> {
                                EventScheduleDto schedule = t.getT1();
                                PaymentDto payment = t.getT2();
                                SeatDto seat = t.getT3();

                                // 3단계: event-service (event, venue, seat class) 호출 (병렬)
                                Mono<EventDto> eventMono = fetchEvent(schedule.getEventId());
                                Mono<VenueDto> venueMono = fetchVenue(schedule.getVenueId());
                                Mono<SeatClassDto> seatClassMono = fetchSeatClass(seat.getSeatClassId());

                                return Mono.zip(eventMono, venueMono, seatClassMono)
                                        .map(t2 -> mapToResponse(booking, account, schedule, payment, seat, t2.getT1(), t2.getT2(), t2.getT3()));
                            });
                });
    }

    private Mono<BookingDto> fetchBooking(Long bookingId, Long accountId) {
        return bookingServiceWebClient.get()
                .uri("/booking/api/bookings/bff/{bookingId}", bookingId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Booking not found or unauthorized")))
                .bodyToMono(BookingDto.class)
                .filter(booking -> booking.getAccountId().equals(accountId))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Booking not found for the given account")));
    }

    private Mono<AccountDto> fetchAccount(Long accountId) {
        return accountServiceWebClient.get()
                .uri("/account/api/users/{accountId}", accountId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Account not found")))
                .bodyToMono(AccountDto.class);
    }

    private Mono<EventScheduleDto> fetchEventSchedule(Long eventScheduleId) {
        return eventServiceWebClient.get()
                .uri("/event/api/event-schedules/{eventScheduleId}", eventScheduleId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Event schedule not found")))
                .bodyToMono(EventScheduleDto.class);
    }

    private Mono<PaymentDto> fetchPayment(Long paymentId) {
        return paymentServiceWebClient.get()
                .uri("/payment/api/payments/{paymentId}", paymentId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Payment not found")))
                .bodyToMono(PaymentDto.class);
    }

    private Mono<SeatDto> fetchSeat(Long seatId) {
        return eventServiceWebClient.get()
                .uri("/event/api/seats/{seatId}", seatId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Seat not found")))
                .bodyToMono(SeatDto.class);
    }

    private Mono<EventDto> fetchEvent(Long eventId) {
        return eventServiceWebClient.get()
                .uri("/event/api/bff/events/{eventId}", eventId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Event not found")))
                .bodyToMono(EventDto.class);
    }

    private Mono<VenueDto> fetchVenue(Long venueId) {
        return eventServiceWebClient.get()
                .uri("/event/api/venues/{venueId}", venueId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Venue not found")))
                .bodyToMono(VenueDto.class);
    }

    private Mono<SeatClassDto> fetchSeatClass(Long seatClassId) {
        return eventServiceWebClient.get()
                .uri("/event/api/seat-classes/{seatClassId}", seatClassId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Seat class not found")))
                .bodyToMono(SeatClassDto.class);
    }

    private BookingDetailResponse mapToResponse(BookingDto booking, AccountDto account, EventScheduleDto schedule,
                                                PaymentDto payment, SeatDto seat, EventDto event, VenueDto venue, SeatClassDto seatClass) {
        return BookingDetailResponse.builder()
                .bookingId(booking.getBookingId())
                .status(booking.getStatus().getDisplayValue())
                .createdAt(booking.getUpdatedAt())
                .user(BookingDetailResponse.UserInfo.builder().name(account.getName()).build())
                .event(BookingDetailResponse.EventInfo.builder()
                        .title(event.getTitle())
                        .thumbnailUrl(event.getThumbnailUrl())
                        .eventDate(schedule.getEventDate())
                        .venue(BookingDetailResponse.EventInfo.VenueInfo.builder().name(venue.getVenueName()).build())
                        .build())
                .seat(BookingDetailResponse.SeatInfo.builder()
                        .className(seatClass.getClassName())
                        .floor(seat.getFloor())
                        .row(seat.getRow())
                        .col(seat.getCol())
                        .build())
                .payment(BookingDetailResponse.PaymentInfo.builder()
                        .method(payment.getMethod())
                        .status(payment.getStatus().getDisplayValue())
                        .paidAt(payment.getUpdatedAt().toString())
                        .amount(payment.getAmount())
                        .build())
                .build();
    }
}