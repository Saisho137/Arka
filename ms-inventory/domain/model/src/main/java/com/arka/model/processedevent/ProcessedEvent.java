package com.arka.model.processedevent;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record ProcessedEvent(
        UUID eventId,
        Instant processedAt
) {
    public ProcessedEvent {
        java.util.Objects.requireNonNull(eventId, "eventId is required");
        processedAt = processedAt != null ? processedAt : Instant.now();
    }
}
