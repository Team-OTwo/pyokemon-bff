package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.SeatSelectionResponse;
import com.pyokemon.bff.service.SeatSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class SeatSelectionController {

//    private final SeatSelectionService seatSelectionService;
//
//    /**
//     * 실시간 좌석 선택 정보 조회 (초기 로드)
//     * @param eventScheduleId 공연 일정 ID
//     * @param accountId 사용자 ID
//     * @return 좌석 선택 정보
//     */
//    @GetMapping("/{eventScheduleId}/seats")
//    public Mono<SeatSelectionResponse> getSeats(
//            @PathVariable Long eventScheduleId,
//            @RequestParam Long accountId) {
//        return seatSelectionService.getSeats(eventScheduleId, accountId);
//    }
//
//    /**
//     * 실시간 좌석 선택 정보 폴링 (증분 업데이트)
//     * @param eventScheduleId 공연 일정 ID
//     * @param lastUpdatedAt 마지막 업데이트 시간
//     * @return 변경된 좌석 정보
//     */
//    @GetMapping("/{eventScheduleId}/seats/changes")
//    public Mono<SeatSelectionResponse> getSeatChanges(
//            @PathVariable Long eventScheduleId,
//            @RequestParam LocalDateTime lastUpdatedAt) {
//        return seatSelectionService.getSeatChanges(eventScheduleId, lastUpdatedAt);
//    }
}