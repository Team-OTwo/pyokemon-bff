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
import com.pyokemon.bff.dto.response.BookingResponse.*;
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

    /** 공연 일정별 예매 목록(공연 정보는 바깥, items에는 예매 항목만) */
    @Cacheable(value = "bookings", key = "#eventScheduleId")
    public Mono<BookingResponse> getBookings(Long eventScheduleId) {

        // 1) 공통 컨텍스트(한 번만 호출)
        Mono<Ctx> ctxMono = getEventSchedule(eventScheduleId)
                .flatMap(es -> Mono.zip(
                        getEvent(es.getEventId()),
                        getVenue(es.getVenueId())
                ).map(t -> new Ctx(es, t.getT1(), t.getT2())))
                .cache();

        // 2) 예매 목록 Flux
        Flux<BookingDto> bookingsFlux = bookingServiceWebClient.get()
                .uri("/booking/api/bookings/event-schedules/{eventScheduleId}/bookings", eventScheduleId)
                .retrieve()
                .bodyToFlux(BookingDto.class);

        // 3) 각 예매 항목 -> BookingItem 변환
        Mono<java.util.List<BookingItem>> itemsMono = bookingsFlux.flatMap(b -> {

            Mono<AccountDto> accountMono = getAccount(b.getAccountId());
            Mono<PaymentDto> paymentMono = getPayment(b.getPaymentId());
            Mono<SeatDto> seatMono       = getSeat(b.getSeatId());
            Mono<SeatClassDto> seatClassMono = seatMono.flatMap(s -> getSeatClass(s.getSeatClassId()));

            return Mono.zip(accountMono, paymentMono, seatMono, seatClassMono)
                    .map(t -> {
                        AccountDto account   = t.getT1();
                        PaymentDto payment   = t.getT2();
                        SeatDto seat         = t.getT3();
                        SeatClassDto seatCls = t.getT4();

                        SeatInfo seatInfo = SeatInfo.builder()
                                .className(seatCls.getClassName())
                                .floor(seat.getFloor())
                                .row(seat.getRow())
                                .col(seat.getCol())
                                .build();

                        return BookingItem.builder()
                                .bookingId(b.getId())
                                .userName(account.getName())
                                .seat(seatInfo)
                                .totalPrice(payment.getAmount())
                                .status(payment.getStatus().getDisplayValue()) // enum 라벨
                                .build();
                    });
        }, /*concurrency*/ 32).collectList();

        // 4) 최종 래핑
        return Mono.zip(ctxMono, itemsMono)
                .map(t -> {
                    Ctx ctx = t.getT1();
                    java.util.List<BookingItem> items = t.getT2();

                    return BookingResponse.builder()
                            .eventId(ctx.event().getId())
                            .eventTitle(ctx.event().getTitle())
                            .eventDate(ctx.schedule().getEventDate().toLocalDate().toString())
                            .venueName(ctx.venue().getName())
                            .thumbnailUrl(ctx.event().getThumbnailUrl())
                            .items(items)
                            .build();
                });
    }

    // ---- 내부 호출들 ----
    private record Ctx(EventScheduleDto schedule, EventDto event, VenueDto venue) {}

    private Mono<EventScheduleDto> getEventSchedule(Long eventScheduleId) {
        return eventServiceWebClient.get()
                .uri("/event/api/event-schedules/{eventScheduleId}", eventScheduleId)
                .retrieve()
                .bodyToMono(EventScheduleDto.class);
    }

    private Mono<AccountDto> getAccount(Long accountId) {
        return accountServiceWebClient.get()
                .uri("/account/api/users/{accountId}", accountId)
                .retrieve()
                .bodyToMono(AccountDto.class);
    }

    private Mono<PaymentDto> getPayment(Long paymentId) {
        return paymentServiceWebClient.get()
                .uri("/payment/api/payments/{paymentId}", paymentId)
                .retrieve()
                .bodyToMono(PaymentDto.class);
    }

    private Mono<SeatDto> getSeat(Long seatId) {
        return eventServiceWebClient.get()
                .uri("/event/api/seats/{seatId}", seatId) // ← 앞 공백 제거!
                .retrieve()
                .bodyToMono(SeatDto.class);
    }

    private Mono<EventDto> getEvent(Long eventId) {
        return eventServiceWebClient.get()
                .uri("/event/api/bff/events/{eventId}", eventId)
                .retrieve()
                .bodyToMono(EventDto.class);
    }

    private Mono<VenueDto> getVenue(Long venueId) {
        return eventServiceWebClient.get()
                .uri("/event/api/venues/{venueId}", venueId)
                .retrieve()
                .bodyToMono(VenueDto.class);
    }

    private Mono<SeatClassDto> getSeatClass(Long seatClassId) {
        return eventServiceWebClient.get()
                .uri("/event/api/seat-classes/{seatClassId}", seatClassId)
                .retrieve()
                .bodyToMono(SeatClassDto.class);
    }
}
