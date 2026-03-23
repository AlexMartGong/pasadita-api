package com.pasadita.api.services.dashboard;

import com.pasadita.api.dto.dashboard.DashboardStatsDto;

import java.time.LocalDateTime;

public interface DashboardService {

    DashboardStatsDto getStats(LocalDateTime startDate, LocalDateTime endDate);
}
