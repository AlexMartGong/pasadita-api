package com.pasadita.api.controllers;

import com.pasadita.api.dto.dashboard.DashboardStatsDto;
import com.pasadita.api.services.dashboard.DashboardService;
import com.pasadita.api.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    @GetMapping
    public ResponseEntity<DashboardStatsDto> getDashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        if (startDate == null || endDate == null) {
            LocalDateTime nowMexico = DateTimeUtils.nowMexico();
            LocalDateTime startOfMonthMexico = nowMexico.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            startDate = DateTimeUtils.toUtc(startOfMonthMexico);
            endDate = DateTimeUtils.nowUtc();
        }

        return ResponseEntity.ok(dashboardService.getStats(startDate, endDate));
    }
}
