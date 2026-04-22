package com.arka.model.payment;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record PaymentProcessedPayload(
        UUID orderId,
        String transactionId,
        PaymentStatus status
) {}
