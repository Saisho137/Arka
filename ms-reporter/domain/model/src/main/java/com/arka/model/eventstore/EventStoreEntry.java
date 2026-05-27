package com.arka.model.eventstore;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record EventStoreEntry(
        UUID id,
        UUID eventId,
        String eventType,
        String source,
        String aggregateId,
        UUID correlationId,
        Object payload,
        Instant timestamp
) {
    public EventStoreEntry {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(payload, "payload is required");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
