package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.response.TicketDetailResponse;
import com.pyokemon.bff.dto.external.*;
import com.pyokemon.bff.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketDetailService {

    private final WebClient bookingServiceWebClient;
    private final WebClient eventServiceWebClient;
    private final WebClient accountServiceWebClient;

    // --- 각 단계의 데이터를 담을 내부 record (컨텍스트 객체) ---
    private record BookingContext(BookingDto booking) {}
    private record ScheduleInfoContext(BookingDto booking, EventScheduleDto schedule, SeatDto seat, TenantDto tenant) {}

    public Mono<TicketDetailResponse> getBookingDetail(Long bookingId, Long accountId) {
        log.info("Fetching details for bookingId: {}, accountId: {}", bookingId, accountId);

        // 1단계: 예매 정보 조회 후 컨텍스트에 담기
        return fetchBooking(bookingId, accountId)
                .map(BookingContext::new)
                .flatMap(this::fetchScheduleAndSeatInfo)
                .flatMap(this::fetchEventAndVenueInfo)
                .map(this::mapToResponse);
    }


     // 2단계: 공연 일정, 좌석, 테넌트 정보 병렬 조회
    private Mono<ScheduleInfoContext> fetchScheduleAndSeatInfo(BookingContext ctx) {
        Mono<EventScheduleDto> scheduleMono = fetchEventSchedule(ctx.booking().getEventScheduleId());
        Mono<SeatDto> seatMono = fetchSeat(ctx.booking().getSeatId());
        Mono<TenantDto> tenantMono = fetchTenant(ctx.booking().getTenantId());

        return Mono.zip(scheduleMono, seatMono, tenantMono)
                .map(tuple -> new ScheduleInfoContext(
                        ctx.booking(),
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ));
    }

     // 3단계: 공연, 공연장, 좌석 등급 정보 병렬 조회 후 최종 데이터 조합
    private Mono<Tuple4<ScheduleInfoContext, EventDto, VenueDto, SeatClassDto>> fetchEventAndVenueInfo(ScheduleInfoContext ctx) {
        Mono<EventDto> eventMono = fetchEvent(ctx.schedule().getEventId());
        Mono<VenueDto> venueMono = fetchVenue(ctx.schedule().getVenueId());
        Mono<SeatClassDto> seatClassMono = fetchSeatClass(ctx.seat().getSeatClassId());

        return Mono.zip(eventMono, venueMono, seatClassMono)
                .map(tuple -> Tuples.of(ctx, tuple.getT1(), tuple.getT2(), tuple.getT3()));
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

    private Mono<EventScheduleDto> fetchEventSchedule(Long eventScheduleId) {
        return eventServiceWebClient.get()
                .uri("/event/api/event-schedules/{eventScheduleId}", eventScheduleId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Event schedule not found")))
                .bodyToMono(EventScheduleDto.class);
    }

    private Mono<SeatDto> fetchSeat(Long seatId) {
        return eventServiceWebClient.get()
                .uri("/event/api/seats/{seatId}", seatId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Seat not found")))
                .bodyToMono(SeatDto.class);
    }

    private Mono<TenantDto> fetchTenant(Long tenantId) {
        return accountServiceWebClient.get()
                .uri("/account/api/tenants/{tenantId}", tenantId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> Mono.error(new ResourceNotFoundException("Tenant not found")))
                .bodyToMono(TenantDto.class);
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

    private TicketDetailResponse mapToResponse(Tuple4<ScheduleInfoContext, EventDto, VenueDto, SeatClassDto> data) {
        ScheduleInfoContext baseInfo = data.getT1();
        EventDto event = data.getT2();
        VenueDto venue = data.getT3();
        SeatClassDto seatClass = data.getT4();

        BookingDto booking = baseInfo.booking();
        EventScheduleDto schedule = baseInfo.schedule();
        SeatDto seat = baseInfo.seat();
        TenantDto tenant = baseInfo.tenant();

        TicketDetailResponse.EventInfo.VenueInfo venueInfo = TicketDetailResponse.EventInfo.VenueInfo.builder()
                .name(venue.getVenueName())
                .build();

        TicketDetailResponse.EventInfo eventInfo = TicketDetailResponse.EventInfo.builder()
                .title(event.getTitle())
                .thumbnailUrl(event.getThumbnailUrl())
                .eventDate(schedule.getEventDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .venue(venueInfo)
                .build();

        TicketDetailResponse.SeatInfo seatInfo = TicketDetailResponse.SeatInfo.builder()
                .className(seatClass.getClassName())
                .floor(seat.getFloor())
                .row(seat.getRow())
                .col(seat.getCol())
                .build();

        return TicketDetailResponse.builder()
                .bookingId(booking.getBookingId())
                .event(eventInfo)
                .seat(seatInfo)
                .tenantName(tenant.getTenantName())
                .build();
    }
}