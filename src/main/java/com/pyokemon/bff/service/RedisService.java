package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.BookingStatus;
import com.pyokemon.bff.dto.external.BookingDto;
import com.pyokemon.bff.dto.external.SeatClassDto;
import com.pyokemon.bff.dto.external.SeatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Qualifier("eventServiceWebClient")
    private final WebClient eventServiceWebClient;

    @Qualifier("bookingServiceWebClient")
    private final WebClient bookingServiceWebClient;

    // --- Key Patterns ---
    private static final String VENUE_SCHEDULE_KEY_PATTERN = "venue:schedule:%d:%d";
    private static final String SEAT_CLASS_STATUS_KEY_PATTERN = "seat:class:status:%d:%s";
    private static final String SEAT_HOLD_KEY_PATTERN = "seat:hold:%d:%d";
    private static final long SEAT_HOLD_DURATION_SECONDS = 300L; // 5분

    public Mono<Void> initSeatStatuses(Long scheduleId, Long venueId) {
        log.info("BFF: 좌석 상태 초기화 시작: scheduleId={}, venueId={}", scheduleId, venueId);

        Mono<Map<Long, BookingDto>> bookingsMapMono = fetchBookingsForSchedule(scheduleId)
                .collectMap(BookingDto::getSeatId, booking -> booking);

        Mono<List<SeatDto>> seatsMono = fetchSeatsForVenue(venueId).collectList();

        return Mono.zip(bookingsMapMono, seatsMono)
                .flatMap(tuple -> {
                    Map<Long, BookingDto> bookingsMap = tuple.getT1();
                    List<SeatDto> seats = tuple.getT2();

                    if (seats.isEmpty()) {
                        log.warn("BFF: 해당 venue에 좌석이 없습니다: venueId={}", venueId);
                        return Mono.empty();
                    }

                    Map<Long, List<SeatDto>> seatsByClassId = seats.stream()
                            .collect(Collectors.groupingBy(SeatDto::getSeatClassId));
                    List<Long> seatClassIds = new ArrayList<>(seatsByClassId.keySet());

                    return fetchSeatClasses(seatClassIds)
                            .flatMap(seatClassMap -> {
                                String venueScheduleKey = String.format(VENUE_SCHEDULE_KEY_PATTERN, venueId, scheduleId);
                                Mono<Boolean> setVenueScheduleMono = redisTemplate.opsForValue().set(venueScheduleKey, "active", Duration.ofDays(30));

                                Flux<Void> processSeatClassesFlux = Flux.fromIterable(seatsByClassId.entrySet())
                                        .flatMap(entry -> {
                                            SeatClassDto seatClass = seatClassMap.get(entry.getKey());
                                            if (seatClass == null) {
                                                return Mono.empty();
                                            }

                                            String className = seatClass.getClassName();
                                            String classKey = String.format(SEAT_CLASS_STATUS_KEY_PATTERN, scheduleId, className);

                                            Map<String, String> initMap = entry.getValue().stream()
                                                    .collect(Collectors.toMap(
                                                            seat -> String.valueOf(seat.getSeatId()),
                                                            seat -> {
                                                                BookingDto booking = bookingsMap.get(seat.getSeatId());
                                                                if (booking != null && booking.getStatus() == BookingStatus.BOOKED) {
                                                                    return String.valueOf(booking.getId());
                                                                }
                                                                return "";
                                                            }
                                                    ));

                                            Mono<Boolean> hashSetMono = redisTemplate.opsForHash().putAll(classKey, initMap);

                                            Flux<Boolean> pendingHoldsFlux = Flux.fromIterable(entry.getValue())
                                                    .map(seat -> bookingsMap.get(seat.getSeatId()))
                                                    .filter(booking -> booking != null && booking.getStatus() == BookingStatus.PENDING)
                                                    .flatMap(booking -> {
                                                        String holdKey = String.format(SEAT_HOLD_KEY_PATTERN, scheduleId, booking.getSeatId());
                                                        String accountId = String.valueOf(booking.getAccountId());
                                                        return redisTemplate.opsForValue().set(holdKey, accountId, Duration.ofSeconds(SEAT_HOLD_DURATION_SECONDS));
                                                    });

                                            return hashSetMono.thenMany(pendingHoldsFlux).then();
                                        });

                                return setVenueScheduleMono.thenMany(processSeatClassesFlux).then();
                            });
                })
                .doOnSuccess(v -> log.info("BFF: 전체 좌석 상태 초기화 및 예약 동기화 완료: scheduleId={}, venueId={}", scheduleId, venueId))
                .then();
    }

    // --- Private Helper Methods ---
    private Flux<BookingDto> fetchBookingsForSchedule(Long eventScheduleId) {
        return bookingServiceWebClient.get()
                .uri("/booking/api/bookings/event-schedules/{eventScheduleId}/bookings", eventScheduleId)
                .retrieve()
                .bodyToFlux(BookingDto.class);
    }

    private Flux<SeatDto> fetchSeatsForVenue(Long venueId) {
        return eventServiceWebClient.get()
                .uri("/event/api/venues/{venueId}/seats", venueId)
                .retrieve()
                .bodyToFlux(SeatDto.class);
    }

    private Mono<Map<Long, SeatClassDto>> fetchSeatClasses(List<Long> seatClassIds) {
        if (CollectionUtils.isEmpty(seatClassIds)) {
            return Mono.just(Collections.emptyMap());
        }
        return eventServiceWebClient.post()
                .uri("/event/api/seat-classes/_batch")
                .bodyValue(Map.of("ids", seatClassIds))
                .retrieve()
                .bodyToFlux(SeatClassDto.class)
                .collectMap(SeatClassDto::getSeatClassId, sc -> sc);
    }
}