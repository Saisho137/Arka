package com.arka.model.notification;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Builder(toBuilder = true)
public record DomainEventEnvelope(
        String eventId,
        String eventType,
        Instant timestamp,
        String source,
        String correlationId,
        Map<String, Object> payload
) {
    public static final String MS_SOURCE = "ms-notifications";

    public DomainEventEnvelope {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        timestamp = timestamp != null ? timestamp : Instant.now();
        source = source != null ? source : MS_SOURCE;
        payload = payload != null ? payload : Map.of();
    }

    public String payloadString(String key) {
        Object val = payload.get(key);
        return val != null ? val.toString() : "";
    }

    public int payloadInt(String key) {
        Object val = payload.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s && !s.isEmpty()) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        return 0;
    }
}
