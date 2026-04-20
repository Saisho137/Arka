package com.arka.model.outboxevent;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record OrderStatusChangedPayload(
    UUID orderId,
    String previousStatus,
    String newStatus,
    String customerEmail
) {
}
