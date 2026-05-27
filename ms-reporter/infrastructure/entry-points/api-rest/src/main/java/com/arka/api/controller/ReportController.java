package com.arka.api.controller;

import com.arka.api.dto.GenerateReportRequest;
import com.arka.api.dto.ReportResponse;
import com.arka.api.handler.ReportHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Reports")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportHandler reportHandler;

    @Operation(summary = "Generate weekly sales report")
    @ApiResponse(responseCode = "202", description = "Report generation started")
    @PostMapping("/sales/weekly")
    public ResponseEntity<ReportResponse> generateReport(
            @Valid @RequestBody GenerateReportRequest request,
            @RequestHeader(value = "X-User-Email", defaultValue = "unknown") String userEmail,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        return reportHandler.generateReport(request, userEmail, userRole);
    }

    @Operation(summary = "Get report status and download URL")
    @ApiResponse(responseCode = "200", description = "Report details")
    @GetMapping("/{reportId}")
    public ResponseEntity<ReportResponse> getReport(
            @PathVariable("reportId") UUID reportId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        return reportHandler.getReport(reportId, userRole);
    }
}
