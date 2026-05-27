package com.arka.model.event;

import lombok.Builder;

import java.time.Instant;

@Builder
public record DomainEventEnvelope(
        String eventId,
        String eventType,
        Instant timestamp,
        String source,
        String correlationId,
        Object payload
) {
    public static final String MS_SOURCE = "ms-cart";
}
