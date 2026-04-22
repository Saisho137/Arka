package com.arka.model.payment;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record PaymentFailedPayload(
        UUID orderId,
        String reason
) {}
