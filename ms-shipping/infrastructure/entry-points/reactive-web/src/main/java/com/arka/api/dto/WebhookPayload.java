package com.arka.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record WebhookPayload(
        @NotBlank(message = "trackingNumber is required") String trackingNumber,
        @NotBlank(message = "status is required") String status,
        Instant deliveryDate
) {}
