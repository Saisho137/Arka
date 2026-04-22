package com.arka.model.payment;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record Payment(
        UUID paymentId,
        UUID orderId,
        String transactionId,
        PaymentStatus status,
        String failureReason,
        Instant processedAt
) {}

