package com.arka.model.outboxevent;

import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record DomainEventEnvelope(
        String eventId,
        String eventType,
        Instant timestamp,
        String source,
        String correlationId,
        Object payload
) {
}
