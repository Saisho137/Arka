package com.arka.model.report;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder(toBuilder = true)
public record ReportMetadata(
        UUID id,
        String reportType,
        ReportStatus status,
        LocalDate startDate,
        LocalDate endDate,
        String s3Key,
        Long fileSizeBytes,
        String requestedBy,
        Instant requestedAt,
        Instant completedAt,
        String errorMessage
) {
    public ReportMetadata {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = ReportStatus.PROCESSING;
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
