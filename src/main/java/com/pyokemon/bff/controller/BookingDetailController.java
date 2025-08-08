package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.BookingDetailResponse;
import com.pyokemon.bff.service.BookingDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingDetailController {

    private final BookingDetailService bookingDetailService;

    /**
     * 예매 상세 정보 조회
     * @param bookingId 예매 ID
     * @param accountId 사용자 ID
     * @return 예매 상세 정보
     */
    @GetMapping("/{bookingId}/details")
    public Mono<BookingDetailResponse> getBookingDetail(
            @PathVariable Long bookingId,
            @RequestParam Long accountId) {
        return bookingDetailService.getBookingDetail(bookingId, accountId);
    }
}