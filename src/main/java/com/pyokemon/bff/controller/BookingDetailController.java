package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.BookingDetailResponse;
import com.pyokemon.bff.service.BookingDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/mypage/bookings")
@RequiredArgsConstructor
public class BookingDetailController {

    private final BookingDetailService bookingDetailService;

    /**
     * 사용자 예매 상세 정보 조회
     *
     * 이 엔드포인트는 주어진 예매 ID와 Gateway에서 제공된 x-auth-accountId 헤더를 기반으로 예매 상세 정보를 조회합니다.
     * - bookingId: 예매 ID (쿼리 파라미터, 필수)
     * - x-auth-accountId: Gateway에서 제공된 사용자 ID (헤더, 필수)
     *
     * @param bookingId 예매 ID
     * @param exchange ServerWebExchange로 x-auth-accountId 헤더 추출
     * @return Mono<BookingDetailResponse> 예매 상세 정보
     */
    @GetMapping("/detail")
    public Mono<BookingDetailResponse> getBookingDetail(
            @RequestParam Long bookingId,
            ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("x-auth-accountId"))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Missing x-auth-accountId header")))
                .map(Long::valueOf)
                .flatMap(accountId -> bookingDetailService.getBookingDetail(bookingId, accountId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Booking not found")))
                .onErrorMap(ex -> new IllegalStateException("Failed to fetch booking details: " + ex.getMessage()));
    }
}