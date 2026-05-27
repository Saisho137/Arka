package com.arka.model.report.gateways;

import com.arka.model.report.ReportMetadata;
import com.arka.model.report.ReportStatus;

import java.util.Optional;
import java.util.UUID;

public interface ReportMetadataRepository {

    void save(ReportMetadata metadata);

    Optional<ReportMetadata> findById(UUID id);

    void updateStatus(UUID id, ReportStatus status, String s3Key, Long fileSizeBytes, String errorMessage);
}
