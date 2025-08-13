package com.pyokemon.bff.service;

import com.pyokemon.bff.dto.external.BookingDto;
import com.pyokemon.bff.dto.external.EventScheduleDto;
import com.pyokemon.bff.dto.external.SeatPriceDto;
import com.pyokemon.bff.dto.external.SeatClassDto;
import com.pyokemon.bff.dto.external.SeatDto;
import com.pyokemon.bff.dto.external.VenueDto;
import com.pyokemon.bff.dto.response.SeatSelectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatSelectionService {
//
//    private final WebClient bookingServiceWebClient;
//    private final WebClient eventServiceWebClient;
//    private final RedisTemplate<String, String> seatHoldRedisTemplate;
//    private final RedisTemplate<String, Object> redisTemplate;
//
//    private static final String SEAT_HOLD_PREFIX = "seat:hold:";
//    private static final String SEAT_CHANGE_PREFIX = "seat:change:";
//
//    /**
//     * 실시간 좌석 선택 정보 조회 (초기 로드)
//     * @param eventScheduleId 공연 일정 ID
//     * @param accountId 사용자 ID
//     * @return 좌석 선택 정보
//     */
//    public Mono<SeatSelectionResponse> getSeats(Long eventScheduleId, Long accountId) {
//        // 1단계: 스케줄/예매좌석 동시 조회
//        Mono<EventScheduleDto> eventScheduleMono = getEventSchedule(eventScheduleId);
//
//        Flux<BookingDto> bookedSeatsFlux = bookingServiceWebClient.get()
//                .uri(uriBuilder -> uriBuilder
//                        .path("/api/v1/bookings")
//                        .queryParam("event_schedule_id", eventScheduleId)
//                        .queryParam("status", "BOOKED,PAID")
//                        .build())
//                .retrieve()
//                .bodyToFlux(BookingDto.class);
//
//        return eventScheduleMono.flatMap(eventSchedule -> {
//            Long venueId = eventSchedule.getVenueId();
//
//            // 2단계: 공연장 좌석 전체 조회
//            Mono<VenueDto> venueMono = getVenue(venueId);
//            Flux<SeatDto> seatsFlux = eventServiceWebClient.get()
//                    .uri(uriBuilder -> uriBuilder
//                            .path("/api/v1/seats")
//                            .queryParam("venue_id", venueId)
//                            .build())
//                    .retrieve()
//                    .bodyToFlux(SeatDto.class);
//
//            // 3단계: 좌석 등급/가격 병렬 조회
//            Flux<SeatClassDto> seatClassesFlux = eventServiceWebClient.get()
//                    .uri(uriBuilder -> uriBuilder
//                            .path("/api/v1/seat-classes")
//                            .queryParam("venue_id", venueId)
//                            .build())
//                    .retrieve()
//                    .bodyToFlux(SeatClassDto.class);
//
//            Flux<SeatPriceDto> pricesFlux = eventServiceWebClient.get()
//                    .uri(uriBuilder -> uriBuilder
//                            .path("/api/v1/prices")
//                            .queryParam("event_schedule_id", eventScheduleId)
//                            .build())
//                    .retrieve()
//                    .bodyToFlux(SeatPriceDto.class);
//
//            return Mono.zip(
//                    venueMono,
//                    seatsFlux.collectList(),
//                    seatClassesFlux.collectList(),
//                    pricesFlux.collectList(),
//                    bookedSeatsFlux.collectList()
//            ).map(tuple -> {
//                VenueDto venue = tuple.getT1();
//                List<SeatDto> allSeats = tuple.getT2();
//                List<SeatClassDto> seatClasses = tuple.getT3();
//                List<SeatPriceDto> prices = tuple.getT4();
//                List<BookingDto> bookedSeats = tuple.getT5();
//
//                // 예매된 좌석 ID 목록
//                Set<Long> bookedSeatIds = bookedSeats.stream()
//                        .map(BookingDto::getSeatId)
//                        .collect(Collectors.toSet());
//
//                // 홀드된 좌석 ID 목록 (Redis에서 조회)
//                Set<String> heldSeatKeys = seatHoldRedisTemplate.keys(SEAT_HOLD_PREFIX + eventScheduleId + ":*");
//                Set<String> heldSeatIds = heldSeatKeys != null ?
//                        heldSeatKeys.stream()
//                                .map(key -> key.substring(key.lastIndexOf(':') + 1))
//                                .collect(Collectors.toSet()) :
//                        Set.of();
//
//                // 좌석 클래스별 통계
//                Map<Long, SeatSelectionResponse.SeatClassInfo> seatClassInfoMap = new HashMap<>();
//
//                // 좌석 클래스별 가격 매핑
//                Map<Long, Integer> seatClassPriceMap = prices.stream()
//                        .collect(Collectors.toMap(SeatPriceDto::getSeatClassId, SeatPriceDto::getPrice));
//
//                // 좌석 클래스별 정보 초기화
//                seatClasses.forEach(seatClass -> {
//                    Integer price = seatClassPriceMap.getOrDefault(seatClass.getId(), 0);
//
//                    seatClassInfoMap.put(seatClass.getId(), SeatSelectionResponse.SeatClassInfo.builder()
//                            .className(seatClass.getClassName())
//                            .priority(seatClass.getPriority())
//                            .price(price)
//                            .availableSeats(0)
//                            .totalSeats(0)
//                            .build());
//                });
//
//                // 좌석 통계 계산
//                allSeats.forEach(seat -> {
//                    SeatSelectionResponse.SeatClassInfo info = seatClassInfoMap.get(seat.getSeatClassId());
//                    if (info != null) {
//                        info.setTotalSeats(info.getTotalSeats() + 1);
//
//                        boolean isBooked = bookedSeatIds.contains(seat.getId());
//                        boolean isHeld = heldSeatIds.contains(seat.getId().toString());
//
//                        if (!isBooked && !isHeld) {
//                            info.setAvailableSeats(info.getAvailableSeats() + 1);
//                        }
//
//                        // 좌석 상태 변경 정보를 Redis에 저장 (증분 업데이트용)
//                        String status = isBooked ? "BOOKED" : (isHeld ? "HELD" : "AVAILABLE");
//                        String changeKey = SEAT_CHANGE_PREFIX + eventScheduleId + ":" + seat.getId();
//                        redisTemplate.opsForHash().put(changeKey, "status", status);
//                        redisTemplate.opsForHash().put(changeKey, "timestamp", LocalDateTime.now().toString());
//                    }
//                });
//
//                // 응답 생성
//                SeatSelectionResponse.EventInfo.VenueInfo venueInfo = SeatSelectionResponse.EventInfo.VenueInfo.builder()
//                        .name(venue.getName())
//                        .build();
//
//                SeatSelectionResponse.EventInfo eventInfo = SeatSelectionResponse.EventInfo.builder()
//                        .eventScheduleId(eventScheduleId)
//                        .venue(venueInfo)
//                        .build();
//
//                return SeatSelectionResponse.builder()
//                        .event(eventInfo)
//                        .seatClasses(new ArrayList<>(seatClassInfoMap.values()))
//                        .lastUpdatedAt(LocalDateTime.now())
//                        .build();
//            });
//        });
//    }
//
//    /**
//     * 실시간 좌석 선택 정보 폴링 (증분 업데이트)
//     * @param eventScheduleId 공연 일정 ID
//     * @param lastUpdatedAt 마지막 업데이트 시간
//     * @return 변경된 좌석 정보
//     */
//    public Mono<SeatSelectionResponse> getSeatChanges(Long eventScheduleId, LocalDateTime lastUpdatedAt) {
//        // Redis에서 마지막 업데이트 이후 변경된 좌석 정보 조회
//        String pattern = SEAT_CHANGE_PREFIX + eventScheduleId + ":*";
//        Set<String> changeKeys = redisTemplate.keys(pattern);
//
//        if (changeKeys == null || changeKeys.isEmpty()) {
//            return Mono.just(SeatSelectionResponse.builder()
//                    .eventScheduleId(eventScheduleId)
//                    .lastUpdatedAt(LocalDateTime.now())
//                    .changedSeats(List.of())
//                    .build());
//        }
//
//        List<SeatSelectionResponse.ChangedSeatInfo> changedSeats = new ArrayList<>();
//
//        changeKeys.forEach(key -> {
//            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
//            String timestamp = (String) entries.get("timestamp");
//            LocalDateTime changeTime = LocalDateTime.parse(timestamp);
//
//            // 마지막 업데이트 이후 변경된 좌석만 포함
//            if (changeTime.isAfter(lastUpdatedAt)) {
//                String seatId = key.substring(key.lastIndexOf(':') + 1);
//                String status = (String) entries.get("status");
//
//                changedSeats.add(SeatSelectionResponse.ChangedSeatInfo.builder()
//                        .seatId(seatId)
//                        .status(status)
//                        .build());
//            }
//        });
//
//        return Mono.just(SeatSelectionResponse.builder()
//                .eventScheduleId(eventScheduleId)
//                .lastUpdatedAt(LocalDateTime.now())
//                .changedSeats(changedSeats)
//                .build());
//    }
//
//    /**
//     * 좌석 홀드 (임시 예약)
//     * @param eventScheduleId 공연 일정 ID
//     * @param seatId 좌석 ID
//     * @param accountId 사용자 ID
//     * @param ttl 홀드 유효 시간 (초)
//     * @return 성공 여부
//     */
//    public Mono<Boolean> holdSeat(Long eventScheduleId, Long seatId, Long accountId, long ttl) {
//        String key = SEAT_HOLD_PREFIX + eventScheduleId + ":" + seatId;
//        String value = accountId.toString();
//
//        Boolean result = seatHoldRedisTemplate.opsForValue().setIfAbsent(key, value, ttl, TimeUnit.SECONDS);
//
//        if (Boolean.TRUE.equals(result)) {
//            // 좌석 상태 변경 정보 저장
//            String changeKey = SEAT_CHANGE_PREFIX + eventScheduleId + ":" + seatId;
//            redisTemplate.opsForHash().put(changeKey, "status", "HELD");
//            redisTemplate.opsForHash().put(changeKey, "timestamp", LocalDateTime.now().toString());
//        }
//
//        return Mono.justOrEmpty(result);
//    }
//
//    /**
//     * 좌석 홀드 해제
//     * @param eventScheduleId 공연 일정 ID
//     * @param seatId 좌석 ID
//     * @param accountId 사용자 ID
//     * @return 성공 여부
//     */
//    public Mono<Boolean> releaseSeat(Long eventScheduleId, Long seatId, Long accountId) {
//        String key = SEAT_HOLD_PREFIX + eventScheduleId + ":" + seatId;
//        String value = seatHoldRedisTemplate.opsForValue().get(key);
//
//        if (value != null && value.equals(accountId.toString())) {
//            Boolean result = seatHoldRedisTemplate.delete(key);
//
//            if (Boolean.TRUE.equals(result)) {
//                // 좌석 상태 변경 정보 저장
//                String changeKey = SEAT_CHANGE_PREFIX + eventScheduleId + ":" + seatId;
//                redisTemplate.opsForHash().put(changeKey, "status", "AVAILABLE");
//                redisTemplate.opsForHash().put(changeKey, "timestamp", LocalDateTime.now().toString());
//            }
//
//            return Mono.justOrEmpty(result);
//        }
//
//        return Mono.just(false);
//    }
//
//    private Mono<EventScheduleDto> getEventSchedule(Long eventScheduleId) {
//        return eventServiceWebClient.get()
//                .uri("/api/v1/event-schedules/{id}", eventScheduleId)
//                .retrieve()
//                .bodyToMono(EventScheduleDto.class);
//    }
//
//    private Mono<VenueDto> getVenue(Long venueId) {
//        return eventServiceWebClient.get()
//                .uri("/api/v1/venues/{id}", venueId)
//                .retrieve()
//                .bodyToMono(VenueDto.class);
//    }
}