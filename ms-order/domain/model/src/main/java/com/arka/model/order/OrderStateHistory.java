package com.arka.model.order;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record OrderStateHistory(
    UUID id,
    UUID orderId,
    String previousStatus,
    String newStatus,
    UUID changedBy,
    String reason,
    Instant createdAt
) {
    public OrderStateHistory {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(newStatus, "newStatus is required");
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
