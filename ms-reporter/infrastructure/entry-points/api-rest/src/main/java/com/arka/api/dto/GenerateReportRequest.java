package com.arka.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;

@Builder(toBuilder = true)
public record GenerateReportRequest(
        @NotBlank String reportType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
