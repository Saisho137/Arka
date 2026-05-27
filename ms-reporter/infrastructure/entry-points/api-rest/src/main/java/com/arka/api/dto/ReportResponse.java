package com.arka.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder(toBuilder = true)
public record ReportResponse(
        UUID id,
        String reportType,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String downloadUrl,
        Long fileSizeBytes,
        String requestedBy,
        Instant requestedAt,
        Instant completedAt,
        String errorMessage
) {
}
