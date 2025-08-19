package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.BookingResponse;
import com.pyokemon.bff.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * 테넌트 예매 현황 조회
     * @param eventScheduleId 공연 일정 ID
     * @return 예매 목록
     */
    @GetMapping
    public Mono<BookingResponse> getBookings(
            @RequestParam Long eventScheduleId) {
        return bookingService.getBookings(eventScheduleId);
    }
}