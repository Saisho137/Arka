package com.arka.model.eventstore;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;

@Builder(toBuilder = true)
public record DomainEventEnvelope(
        String eventId,
        String eventType,
        Instant timestamp,
        String source,
        String correlationId,
        Object payload
) {
    public static final String MS_SOURCE = "ms-reporter";

    public DomainEventEnvelope {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(payload, "payload is required");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (source == null) {
            source = MS_SOURCE;
        }
    }
}
