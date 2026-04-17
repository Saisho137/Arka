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
        // id is nullable — DB generates UUID via DEFAULT gen_random_uuid()
        status = status != null ? status : OutboxStatus.PENDING;
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public boolean isPending() {
        return status == OutboxStatus.PENDING;
    }

    public boolean isPublished() {
        return status == OutboxStatus.PUBLISHED;
    }

    public OutboxEvent markAsPublished() {
        if (status != OutboxStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot publish outbox event " + id + ". Current status: " + status + ", expected: PENDING");
        }
        return this.toBuilder().status(OutboxStatus.PUBLISHED).build();
    }
}
