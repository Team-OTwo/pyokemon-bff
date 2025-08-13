package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.MyPageBookingResponse;
import com.pyokemon.bff.service.MyPageBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/mypage/bookings")
@RequiredArgsConstructor
public class MyPageBookingController {

    private final MyPageBookingService myPageBookingService;

    /**
     * 사용자 마이페이지 예매 내역 조회
     *
//     * @param accountId 사용자 ID
     * @return 예매 목록
     */
    @GetMapping
    public Flux<MyPageBookingResponse> getMyBookings(ServerWebExchange exchange) {
        System.out.println(exchange.getRequest().getHeaders().getFirst("x-auth-accountId"));
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("x-auth-accountId"))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Missing x-auth-accountId header")))
                .map(Long::valueOf)
                .flatMapMany(myPageBookingService::getMyBookings);
    }
}