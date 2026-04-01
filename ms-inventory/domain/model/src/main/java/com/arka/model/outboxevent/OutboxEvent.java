package com.arka.model.outboxevent;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record OutboxEvent(
        UUID id,
        String eventType,
        String payload,
        String partitionKey,
        String status,
        Instant createdAt
) {
    public OutboxEvent {
        id = id != null ? id : UUID.randomUUID();
        status = status != null ? status : "PENDING";
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
