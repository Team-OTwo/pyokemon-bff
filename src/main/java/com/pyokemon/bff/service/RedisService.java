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

    // --- Lua Scripts for Atomic Operations ---
    private static final RedisScript<Long> HOLD_SEAT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('hget', KEYS[1], ARGV[1]) == '' then " +
            "  if redis.call('set', KEYS[2], ARGV[2], 'EX', ARGV[3], 'NX') then return 1 else return 0 end " +
            "else return 0 end", Long.class);

    private static final RedisScript<Long> RELEASE_SEAT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else return 0 end", Long.class);

    public Mono<Void> initSeatStatuses(Long scheduleId, Long venueId) {
        log.info("BFF: 좌석 상태 초기화 시작: scheduleId={}, venueId={}", scheduleId, venueId);
        return fetchSeatsForVenue(venueId)
                .collectList()
                .flatMap(seats -> {
                    if (seats.isEmpty()) {
                        log.warn("BFF: 해당 venue에 좌석이 없습니다: venueId={}", venueId);
                        return Mono.empty();
                    }
                    Map<Long, List<SeatDto>> seatsByClassId = seats.stream()
                            .collect(Collectors.groupingBy(SeatDto::getSeatClassId));
                    List<Long> seatClassIds = seatsByClassId.keySet().stream().toList();

                    return fetchSeatClasses(seatClassIds)
                            .collectMap(SeatClassDto::getSeatClassId, SeatClassDto::getClassName)
                            .flatMap(seatClassNameMap -> {
                                String venueScheduleKey = String.format(VENUE_SCHEDULE_KEY_PATTERN, venueId, scheduleId);
                                redisTemplate.opsForValue().set(venueScheduleKey, "active", Duration.ofDays(30));

                                for (Map.Entry<Long, List<SeatDto>> entry : seatsByClassId.entrySet()) {
                                    String className = seatClassNameMap.get(entry.getKey());
                                    if (className == null) continue;

                                    String classKey = String.format(SEAT_CLASS_STATUS_KEY_PATTERN, scheduleId, className);
                                    Map<String, String> initMap = entry.getValue().stream()
                                            .collect(Collectors.toMap(seat -> String.valueOf(seat.getSeatId()), seat -> ""));
                                    redisTemplate.opsForHash().putAll(classKey, initMap);
                                }
                                log.info("BFF: 전체 좌석 상태 초기화 완료: scheduleId={}, venueId={}", scheduleId, venueId);
                                return Mono.empty();
                            });
                })
                .then();
    }

    public Mono<Void> synchronizeBookingsWithRDB(Long eventScheduleId) {
        log.info("BFF: RDB와 예약 상태 동기화 시작: eventScheduleId={}", eventScheduleId);
        return fetchBookingsForSchedule(eventScheduleId)
                .flatMap(booking -> {
                    Mono<Void> operation = Mono.empty();
                    if (booking.getStatus() == BookingStatus.BOOKED) {
                        operation = fetchSeatDetails(booking.getSeatId())
                            .doOnNext(seatDetails -> {
                                String hashKey = String.format(SEAT_CLASS_STATUS_KEY_PATTERN, eventScheduleId, seatDetails.getClassName());
                                String seatId = String.valueOf(booking.getSeatId());
                                String bookingId = String.valueOf(booking.getBookingId());
                                redisTemplate.opsForHash().put(hashKey, seatId, bookingId);
                                log.debug("BFF: Redis 좌석 '예약 완료' 상태로 업데이트: scheduleId={}, seatId={}, bookingId={}", eventScheduleId, seatId, bookingId);
                            }).then();
                    } else if (booking.getStatus() == BookingStatus.PENDING) {
                        operation = Mono.fromRunnable(() -> {
                            String holdKey = String.format(SEAT_HOLD_KEY_PATTERN, eventScheduleId, booking.getSeatId());
                            String accountId = String.valueOf(booking.getAccountId());
                            redisTemplate.opsForValue().set(holdKey, accountId, Duration.ofSeconds(SEAT_HOLD_DURATION_SECONDS));
                            log.debug("BFF: Redis 좌석 '임시 점유' 상태로 업데이트: scheduleId={}, seatId={}, accountId={}", eventScheduleId, booking.getSeatId(), accountId);
                        });
                    }
                    return operation;
                })
                .doOnComplete(() -> log.info("BFF: RDB와 예약 상태 동기화 완료: eventScheduleId={}", eventScheduleId))
                .then();
    }

    public Mono<Map<String, String>> getSeatStatusesBySeatClassName(Long scheduleId, String className) {
        log.info("BFF: 좌석 클래스별 상태 조회: scheduleId={}, className={}", scheduleId, className);
        String hashKey = String.format(SEAT_CLASS_STATUS_KEY_PATTERN, scheduleId, className);

        return Mono.fromCallable(() -> {
            Map<Object, Object> rawMap = redisTemplate.opsForHash().entries(hashKey);
            Map<String, String> statusMap = rawMap.entrySet().stream()
                    .collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));

            List<String> availableSeatIds = statusMap.entrySet().stream()
                    .filter(e -> e.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .toList();

            if (CollectionUtils.isEmpty(availableSeatIds)) {
                return statusMap;
            }

            List<String> holdKeys = availableSeatIds.stream()
                    .map(seatId -> String.format(SEAT_HOLD_KEY_PATTERN, scheduleId, Long.parseLong(seatId)))
                    .toList();

            List<String> holdValues = redisTemplate.opsForValue().multiGet(holdKeys);

            if (holdValues != null) {
                for (int i = 0; i < availableSeatIds.size(); i++) {
                    if (holdValues.get(i) != null) {
                        statusMap.put(availableSeatIds.get(i), "HELD");
                    }
                }
            }
            return statusMap;
        });
    }

    public Mono<Boolean> holdSeat(Long scheduleId, Long seatId, String userId) {
        log.info("BFF: 좌석 점유 요청: scheduleId={}, seatId={}, userId={}", scheduleId, seatId, userId);
        return fetchSeatDetails(seatId).flatMap(seatDetails -> {
            String className = seatDetails.getClassName();
            String hashKey = String.format(SEAT_CLASS_STATUS_KEY_PATTERN, scheduleId, className);
            String holdKey = String.format(SEAT_HOLD_KEY_PATTERN, scheduleId, seatId);

            return Mono.fromCallable(() -> redisTemplate.execute(HOLD_SEAT_SCRIPT,
                    List.of(hashKey, holdKey), String.valueOf(seatId), userId, String.valueOf(SEAT_HOLD_DURATION_SECONDS)))
                    .map(result -> result == 1L);
        });
    }

    public Mono<Boolean> releaseSeatHold(Long scheduleId, Long seatId, String userId) {
        log.info("BFF: 좌석 점유 해제 요청: scheduleId={}, seatId={}, userId={}", scheduleId, seatId, userId);
        String holdKey = String.format(SEAT_HOLD_KEY_PATTERN, scheduleId, seatId);
        return Mono.fromCallable(() -> redisTemplate.execute(RELEASE_SEAT_SCRIPT, Collections.singletonList(holdKey), userId))
                .map(result -> result == 1L);
    }

    public Mono<Void> cancelSeat(Long scheduleId, Long seatId) {
        log.info("BFF: 좌석 예약 취소 요청: scheduleId={}, seatId={}", scheduleId, seatId);
        return fetchSeatDetails(seatId).flatMap(seatDetails -> {
            String className = seatDetails.getClassName();
            String hashKey = String.format(SEAT_CLASS_STATUS_KEY_PATTERN, scheduleId, className);
            redisTemplate.opsForHash().put(hashKey, String.valueOf(seatId), "");
            return Mono.empty();
        });
    }

    // --- Private Helper Methods ---
    private Mono<EventScheduleDto> fetchEventSchedule(Long eventScheduleId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/event-schedules/{eventScheduleId}", eventScheduleId)
                .retrieve()
                .bodyToMono(EventScheduleDto.class);
    }

    private Flux<BookingDto> fetchBookingsForSchedule(Long eventScheduleId) {
        return bookingServiceWebClient.get()
                .uri("/api/v1/bookings/schedules/{eventScheduleId}", eventScheduleId)
                .retrieve()
                .bodyToFlux(BookingDto.class);
    }

    private Flux<SeatDto> fetchSeatsForVenue(Long venueId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/venues/{venueId}/seats", venueId)
                .retrieve()
                .bodyToFlux(SeatDto.class);
    }

    private Flux<SeatClassDto> fetchSeatClasses(List<Long> seatClassIds) {
        String ids = seatClassIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return eventServiceWebClient.get()
                .uri("/api/v1/seat-classes?ids={ids}", ids)
                .retrieve()
                .bodyToFlux(SeatClassDto.class);
    }

    private Mono<SeatClassDto> fetchSeatDetails(Long seatId) {
        return eventServiceWebClient.get()
                .uri("/api/v1/seats/{seatId}/details", seatId)
                .retrieve()
                .bodyToMono(SeatClassDto.class);
    }
}
