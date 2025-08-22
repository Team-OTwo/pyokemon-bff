package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.TicketDetailResponse;
import com.pyokemon.bff.service.TicketDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/app/bookings")
@RequiredArgsConstructor
public class TicketDetailController {

    private final TicketDetailService ticketDetailService;

    @GetMapping("/my-tickets/{bookingId}")
    public Mono<TicketDetailResponse> getMyTicketDetail(
            @PathVariable Long bookingId,
            @RequestHeader("x-auth-accountId") Long accountId) {

        log.info("Request received for getMyTicketDetail. bookingId: {}, accountId: {}", bookingId, accountId);
        return ticketDetailService.getBookingDetail(bookingId, accountId);
    }
}
