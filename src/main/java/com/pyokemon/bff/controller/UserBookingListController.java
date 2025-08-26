package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.BookingResponse;
import com.pyokemon.bff.dto.response.CursorPageResponse;
import com.pyokemon.bff.dto.response.PageResponse;
import com.pyokemon.bff.dto.response.UserBookingListResponse;
import com.pyokemon.bff.service.BookingService;
import com.pyokemon.bff.service.UserBookingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/app/bookings/my-tickets")
@RequiredArgsConstructor
public class UserBookingListController {

    private final UserBookingListService userBookingListService;

    /**
     * 사용자 예매 현황 리스트 조회
     * accountId 공연 일정 ID
     * @return 예매 목록
     */
    @GetMapping
    public Mono<CursorPageResponse<UserBookingListResponse>> getBookings(
            @RequestHeader(name = "X-Auth-AccountId") Long accountId,
            @RequestParam(defaultValue = "전체") String genre,
            @RequestParam(required = false) Long cursor) {
        return userBookingListService.getBookings(accountId, genre, cursor);
    }
}