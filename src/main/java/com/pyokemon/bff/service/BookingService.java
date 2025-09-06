package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.external.*;
import com.pyokemon.bff.dto.response.BookingResponse;
import com.pyokemon.bff.dto.response.BookingResponse.BookingItem;
import com.pyokemon.bff.dto.response.BookingResponse.SeatInfo;
import com.pyokemon.bff.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final WebClient bookingServiceWebClient;
    private final WebClient eventServiceWebClient;
    private final WebClient accountServiceWebClient;
    private final WebClient paymentServiceWebClient;

    /** 공연 일정별 예매 목록(공연 정보는 바깥, items에는 예매 항목만) */
    @Cacheable(value = "bookings", key = "T(String).format('%s:%s:%s', #eventScheduleId, #page, #size)")
    public Mono<PageResponse<BookingResponse>> getBookings(Long eventScheduleId, int page, int size) {

        Mono<Ctx> ctxMono = getEventSchedule(eventScheduleId)
                .flatMap(es -> Mono.zip(getEvent(es.getEventId()), getVenue(es.getVenueId()))
                        .map(t -> new Ctx(es, t.getT1(), t.getT2())));

        Mono<PageResponse<BookingDto>> bookingsMono = bookingServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/booking/api/bookings/event-schedules/{eventScheduleId}/bookings/page")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        // 필요 시 .queryParam("sort", "createdAt,desc")
                        .build(eventScheduleId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<PageResponse<BookingDto>>() {});

        Mono<List<BookingDto>> bookingsListMono = bookingsMono.map(PageResponse::getContent);

        // 4) 배치 ID 추출(중복 제거)
        Mono<List<Long>> accountIdsMono = bookingsListMono.map(list ->
                list.stream().map(BookingDto::getAccountId).distinct().toList()
        );
        Mono<List<Long>> paymentIdsMono = bookingsListMono.map(list ->
                list.stream().map(BookingDto::getPaymentId).filter(Objects::nonNull).distinct().toList()
        );
        Mono<List<Long>> seatIdsMono = bookingsListMono.map(list ->
                list.stream().map(BookingDto::getSeatId).distinct().toList()
        );

        // 배치 조회
        Mono<Map<Long, SeatDto>> seatsMapMono = seatIdsMono.flatMap(this::getSeatsBatch);
        Mono<List<Long>> seatClassIdsMono = seatsMapMono.map(m ->
                m.values().stream().map(SeatDto::getSeatClassId).distinct().toList()
        );
        Mono<Map<Long, SeatClassDto>> seatClassesMapMono = seatClassIdsMono.flatMap(this::getSeatClassesBatch);
        Mono<Map<Long, AccountDto>> accountsMapMono = accountIdsMono.flatMap(this::getAccountsBatch);
        Mono<Map<Long, PaymentDto>> paymentsMapMono = paymentIdsMono.flatMap(this::getPaymentsBatch);

        // 아이템 조합
        Mono<List<BookingItem>> itemsMono = Mono.zip(
                accountsMapMono, paymentsMapMono, seatsMapMono, seatClassesMapMono, bookingsListMono
        ).map(t -> {
            Map<Long, AccountDto> accounts = t.getT1();
            Map<Long, PaymentDto> payments = t.getT2();
            Map<Long, SeatDto> seats       = t.getT3();
            Map<Long, SeatClassDto> seatCs = t.getT4();
            List<BookingDto> bookings      = t.getT5();

            return bookings.stream().map(b -> {
                SeatDto seat = seats.get(b.getSeatId());
                SeatClassDto seatCls = (seat != null) ? seatCs.get(seat.getSeatClassId()) : null;
                AccountDto account = accounts.get(b.getAccountId());
                PaymentDto payment = (b.getPaymentId() != null) ? payments.get(b.getPaymentId()) : null;

                SeatInfo seatInfo = SeatInfo.builder()
                        .className(seatCls != null ? seatCls.getClassName() : null)
                        .floor(seat != null ? seat.getFloor() : null)
                        .row(seat != null ? seat.getRow() : null)
                        .col(seat != null ? seat.getCol() : null)
                        .build();

                return BookingItem.builder()
                        .bookingId(b.getId())
                        .userName(account != null ? account.getName() : null)
                        .seat(seatInfo)
                        .totalPrice(payment != null ? payment.getAmount() : null)
                        .status(payment != null ? payment.getStatus().getDisplayValue() : "PENDING")
                        .build();
            }).toList();
        });

        // 최종 반환: PageResponse<BookingResponse>
        return Mono.zip(ctxMono, itemsMono, bookingsMono).map(t -> {
            Ctx ctx = t.getT1();
            List<BookingItem> items = t.getT2();
            PageResponse<BookingDto> bm = t.getT3();

            BookingResponse body = BookingResponse.builder()
                    .eventId(ctx.event().getId())
                    .eventTitle(ctx.event().getTitle())
                    .eventDate(ctx.schedule().getEventDate().toLocalDate().toString())
                    .venueName(ctx.venue().getVenueName())
                    .thumbnailUrl(ctx.event().getThumbnailUrl())
                    .items(items) // 이 페이지의 항목만 포함
                    .build();

            // content에 BookingResponse 하나만 담아 반환
            return new PageResponse<>(
                    List.of(body),
                    bm.getPage(),
                    bm.getTotalCount()
            );
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

    // ==== 배치 호출 헬퍼 ====
    private Mono<Map<Long, AccountDto>> getAccountsBatch(List<Long> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return accountServiceWebClient.post().uri("/account/api/users/_batch")
                .bodyValue(Map.of("ids", ids))
                .retrieve().bodyToFlux(AccountDto.class)
                .collectMap(AccountDto::getAccountId, a -> a);
    }

    private Mono<Map<Long, PaymentDto>> getPaymentsBatch(List<Long> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return paymentServiceWebClient.post().uri("/payment/api/payments/_batch")
                .bodyValue(Map.of("ids", ids))
                .retrieve().bodyToFlux(PaymentDto.class)
                .collectMap(PaymentDto::getId, p -> p);
    }

    private Mono<Map<Long, SeatDto>> getSeatsBatch(List<Long> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return eventServiceWebClient.post().uri("/event/api/seats/_batch")
                .bodyValue(Map.of("ids", ids))
                .retrieve().bodyToFlux(SeatDto.class)
                .collectMap(SeatDto::getId, s -> s);
    }

    private Mono<Map<Long, SeatClassDto>> getSeatClassesBatch(List<Long> ids) {
        if (ids.isEmpty()) return Mono.just(Map.of());
        return eventServiceWebClient.post().uri("/event/api/seat-classes/_batch")
                .bodyValue(Map.of("ids", ids))
                .retrieve().bodyToFlux(SeatClassDto.class)
                .collectMap(SeatClassDto::getId, sc -> sc);
    }
}