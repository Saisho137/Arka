package com.arka.model.outboxevent;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record OutboxEvent(
        UUID id,
        EventType eventType,
        String payload,
        String partitionKey,
        OutboxStatus status,
        Instant createdAt
) {
    public OutboxEvent {
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(partitionKey, "partitionKey is required");
        id = id != null ? id : UUID.randomUUID();
        status = status != null ? status : OutboxStatus.PENDING;
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public boolean isPending() {
        return status == OutboxStatus.PENDING;
    }

    public boolean isPublished() {
        return status == OutboxStatus.PUBLISHED;
    }
}
