package com.pyokemon.bff.controller;

import com.pyokemon.bff.dto.response.TenantDashboardResponseDto;
import com.pyokemon.bff.service.TenantDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/events/tenant")
@RequiredArgsConstructor
public class TenantDashboardController {

    private final TenantDashboardService tenantDashboardService;

    @GetMapping("/monthly-summary")
    public Mono<ResponseEntity<TenantDashboardResponseDto>> getTenantDashboard(
            @RequestHeader("x-auth-accountId") Long tenantId,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {

        return tenantDashboardService.getTenantDashboard(tenantId, year, month)
                .map(ResponseEntity::ok);
    }
}


