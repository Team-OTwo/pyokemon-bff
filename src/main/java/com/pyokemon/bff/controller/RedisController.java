package com.pyokemon.bff.controller;

import com.pyokemon.bff.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/redis")
@RequiredArgsConstructor
public class RedisController {

    private final RedisService redisService;

    @PostMapping("/seats/init")
    public Mono<ResponseEntity<Void>> initSeatStatuses(@RequestParam Long scheduleId, @RequestParam Long venueId) {
        return redisService.initSeatStatuses(scheduleId, venueId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}