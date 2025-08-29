package com.pyokemon.bff.controller;

import com.pyokemon.bff.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/redis")
@RequiredArgsConstructor
public class RedisController {

    private final RedisService redisService;

    @PostMapping("/seats/init")
    public Mono<ResponseEntity<Void>> initSeatStatuses(@RequestParam Long scheduleId, @RequestParam Long venueId) {
        return redisService.initSeatStatuses(scheduleId, venueId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @GetMapping("/seats/status")
    public Mono<ResponseEntity<Map<String, String>>> getSeatStatusesBySeatClassName(@RequestParam Long scheduleId, @RequestParam String className) {
        return redisService.getSeatStatusesBySeatClassName(scheduleId, className)
                .map(ResponseEntity::ok);
    }

    private Mono<ResponseEntity<Void>> processBooleanResponse(Mono<Boolean> action) {
        return action.flatMap(success -> {
            if (success) {
                return Mono.just(ResponseEntity.ok().<Void>build());
            } else {
                return Mono.just(ResponseEntity.status(409).<Void>build()); // 409 Conflict
            }
        });
    }

    @PostMapping("/seats/hold")
    public Mono<ResponseEntity<Void>> holdSeat(@RequestParam Long scheduleId, @RequestParam Long seatId, @RequestParam String userId) {
        return processBooleanResponse(redisService.holdSeat(scheduleId, seatId, userId));
    }

    @PostMapping("/seats/release")
    public Mono<ResponseEntity<Void>> releaseSeatHold(@RequestParam Long scheduleId, @RequestParam Long seatId, @RequestParam String userId) {
        return processBooleanResponse(redisService.releaseSeatHold(scheduleId, seatId, userId));
    }

    @PostMapping("/seats/cancel")
    public Mono<ResponseEntity<Void>> cancelSeat(@RequestParam Long scheduleId, @RequestParam Long seatId) {
        return redisService.cancelSeat(scheduleId, seatId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}