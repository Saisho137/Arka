package com.arka.model.outbox;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record OutboxEvent(
        UUID id,
        EventType eventType,
        String topic,
        String partitionKey,
        String payload,
        OutboxStatus status,
        Instant createdAt
) {
    public OutboxEvent {
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(partitionKey, "partitionKey is required");
        topic = topic != null ? topic : "provider-events";
        status = status != null ? status : OutboxStatus.PENDING;
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public OutboxEvent markAsPublished() {
        if (status != OutboxStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot publish outbox event " + id + ". Current status: " + status + ", expected: PENDING");
        }
        return this.toBuilder().status(OutboxStatus.PUBLISHED).build();
    }
}
