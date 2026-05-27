package com.arka.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record UpdateStatusRequest(
        @NotBlank(message = "status is required") String status,
        String failureReason,
        Instant actualDeliveryDate
) {}
