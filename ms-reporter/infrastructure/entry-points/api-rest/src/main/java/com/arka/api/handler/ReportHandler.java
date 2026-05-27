package com.arka.api.handler;

import com.arka.api.dto.GenerateReportRequest;
import com.arka.api.dto.ReportResponse;
import com.arka.model.commons.exception.AccessDeniedException;
import com.arka.model.report.ReportMetadata;
import com.arka.model.report.ReportStatus;
import com.arka.usecase.reportgeneration.ReportGenerationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReportHandler {

    private final ReportGenerationUseCase reportGenerationUseCase;

    public ResponseEntity<ReportResponse> generateReport(GenerateReportRequest request, String userEmail, String userRole) {
        validateAdminRole(userRole);

        UUID reportId = reportGenerationUseCase.generateWeeklyReport(
                request.reportType(), request.startDate(), request.endDate(), userEmail);

        // Trigger async generation
        reportGenerationUseCase.executeReportGeneration(reportId);

        ReportResponse response = ReportResponse.builder()
                .id(reportId)
                .reportType(request.reportType())
                .status("PROCESSING")
                .startDate(request.startDate())
                .endDate(request.endDate())
                .requestedBy(userEmail)
                .build();

        return ResponseEntity.accepted()
                .location(URI.create("/reports/" + reportId))
                .body(response);
    }

    public ResponseEntity<ReportResponse> getReport(UUID reportId, String userRole) {
        validateAdminRole(userRole);

        ReportMetadata metadata = reportGenerationUseCase.getReport(reportId);
        String downloadUrl = metadata.status() == ReportStatus.COMPLETED
                ? reportGenerationUseCase.getDownloadUrl(reportId)
                : null;

        ReportResponse response = ReportResponse.builder()
                .id(metadata.id())
                .reportType(metadata.reportType())
                .status(metadata.status().name())
                .startDate(metadata.startDate())
                .endDate(metadata.endDate())
                .downloadUrl(downloadUrl)
                .fileSizeBytes(metadata.fileSizeBytes())
                .requestedBy(metadata.requestedBy())
                .requestedAt(metadata.requestedAt())
                .completedAt(metadata.completedAt())
                .errorMessage(metadata.errorMessage())
                .build();

        return ResponseEntity.ok(response);
    }

    private void validateAdminRole(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Only ADMIN role can access reports");
        }
    }
}
