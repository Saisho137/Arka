package com.arka.usecase.reportgeneration;

import com.arka.model.commons.exception.InvalidDateRangeException;
import com.arka.model.commons.exception.ReportNotFoundException;
import com.arka.model.report.ReportMetadata;
import com.arka.model.report.ReportStatus;
import com.arka.model.report.gateways.FileStorageGateway;
import com.arka.model.report.gateways.ReportMetadataRepository;
import com.arka.model.sales.SalesSummary;
import com.arka.model.sales.gateways.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ReportGenerationUseCase {

    private final ReportMetadataRepository reportMetadataRepository;
    private final SalesSummaryRepository salesSummaryRepository;
    private final FileStorageGateway fileStorageGateway;
    private final ReportFileGenerator reportFileGenerator;

    public UUID generateWeeklyReport(String reportType, LocalDate startDate, LocalDate endDate, String requestedBy) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("Start date must be before or equal to end date");
        }

        ReportMetadata metadata = ReportMetadata.builder()
                .reportType(reportType)
                .startDate(startDate)
                .endDate(endDate)
                .requestedBy(requestedBy)
                .build();

        reportMetadataRepository.save(metadata);
        return metadata.id();
    }

    public void executeReportGeneration(UUID reportId) {
        ReportMetadata metadata = reportMetadataRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId.toString()));

        try {
            List<SalesSummary> data = salesSummaryRepository.findByWeekRange(
                    metadata.startDate(), metadata.endDate());

            ReportFileResult result = reportFileGenerator.generate(metadata.reportType(), data, metadata);
            String s3Key = "reports/" + metadata.reportType() + "/" + reportId + result.extension();

            fileStorageGateway.upload(s3Key, result.filePath(), result.contentType());

            long fileSize = result.filePath().toFile().length();
            reportMetadataRepository.updateStatus(reportId, ReportStatus.COMPLETED, s3Key, fileSize, null);

            log.info("Report generated successfully: id={}, s3Key={}, size={}", reportId, s3Key, fileSize);
        } catch (Exception e) {
            log.error("Report generation failed: id={}", reportId, e);
            reportMetadataRepository.updateStatus(reportId, ReportStatus.FAILED, null, null, e.getMessage());
        }
    }

    public ReportMetadata getReport(UUID reportId) {
        return reportMetadataRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId.toString()));
    }

    public String getDownloadUrl(UUID reportId) {
        ReportMetadata metadata = getReport(reportId);
        if (metadata.status() != ReportStatus.COMPLETED || metadata.s3Key() == null) {
            throw new ReportNotFoundException("Report not ready for download: " + reportId);
        }
        return fileStorageGateway.generatePresignedUrl(metadata.s3Key());
    }
}

