package com.arka.api.controller;

import com.arka.api.dto.KpiResponse;
import com.arka.api.handler.MetricsHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Metrics")
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsHandler metricsHandler;

    @Operation(summary = "Get business KPIs for a date range")
    @ApiResponse(responseCode = "200", description = "KPI results")
    @GetMapping("/kpis")
    public ResponseEntity<KpiResponse> getKpis(
            @RequestParam(value = "startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        return metricsHandler.getKpis(startDate, endDate, userRole);
    }
}
