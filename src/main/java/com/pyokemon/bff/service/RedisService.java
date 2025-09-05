package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.BookingStatus;
import com.pyokemon.bff.dto.external.BookingDto;
import com.pyokemon.bff.dto.external.EventScheduleDto;
import com.pyokemon.bff.dto.external.SeatClassDto;
import com.pyokemon.bff.dto.external.SeatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

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
                    List<Long> seatClassIds = seatsByClassId.keySet().stream().toList();

                    return fetchSeatClasses(seatClassIds)
                            .flatMap(seatClassMap -> {
                                String venueScheduleKey = String.format(VENUE_SCHEDULE_KEY_PATTERN, venueId, scheduleId);
                                redisTemplate.opsForValue().set(venueScheduleKey, "active", Duration.ofDays(30));

                                for (Map.Entry<Long, List<SeatDto>> entry : seatsByClassId.entrySet()) {
                                    SeatClassDto seatClass = seatClassMap.get(entry.getKey());
                                    if (seatClass == null) continue;

                                    String className = seatClass.getClassName();
                                    String classKey = String.format(SEAT_CLASS_STATUS_KEY_PATTERN, scheduleId, className);
                                    Map<String, String> initMap = entry.getValue().stream()
                                            .collect(Collectors.toMap(
                                                    seat -> String.valueOf(seat.getSeatId()),
                                                    seat -> {
                                                        BookingDto booking = bookingsMap.get(seat.getSeatId());
                                                        if (booking != null) {
                                                            if (booking.getStatus() == BookingStatus.BOOKED) {
                                                                return String.valueOf(booking.getBookingId());
                                                            } else if (booking.getStatus() == BookingStatus.PENDING) {
                                                                String holdKey = String.format(SEAT_HOLD_KEY_PATTERN, scheduleId, seat.getSeatId());
                                                                String accountId = String.valueOf(booking.getAccountId());
                                                                redisTemplate.opsForValue().set(holdKey, accountId, Duration.ofSeconds(SEAT_HOLD_DURATION_SECONDS));
                                                                log.debug("BFF: Redis 좌석 '임시 점유' 상태로 초기화: scheduleId={}, seatId={}, accountId={}", scheduleId, seat.getSeatId(), accountId);
                                                                return ""; 
                                                            }
                                                        }
                                                        return ""; 
                                                    }
                                            ));
                                    redisTemplate.opsForHash().putAll(classKey, initMap);
                                }
                                log.info("BFF: 전체 좌석 상태 초기화 및 예약 동기화 완료: scheduleId={}, venueId={}", scheduleId, venueId);
                                return Mono.empty();
                            });
                })
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