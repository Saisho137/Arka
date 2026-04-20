package com.arka.model.outboxevent;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record OrderCancelledPayload(
    UUID orderId,
    UUID customerId,
    String customerEmail,
    String reason
) {
}
