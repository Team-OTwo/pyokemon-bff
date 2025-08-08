package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.MyPageBookingResponse;
import com.pyokemon.bff.service.MyPageBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/mypage/bookings")
@RequiredArgsConstructor
public class MyPageBookingController {

    private final MyPageBookingService myPageBookingService;

    /**
     * 사용자 마이페이지 예매 내역 조회
     * @param accountId 사용자 ID
     * @return 예매 목록
     */
    @GetMapping
    public Flux<MyPageBookingResponse> getMyBookings(@RequestParam Long accountId) {
        return myPageBookingService.getMyBookings(accountId);
    }
}