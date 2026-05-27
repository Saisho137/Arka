package com.arka.model.eventstore;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record EventEnvelope(
        UUID eventId,
        String eventType,
        Instant timestamp,
        String source,
        UUID correlationId,
        Object payload
) {
    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(payload, "payload is required");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
