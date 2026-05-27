package com.arka.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record EventEntryResponse(
        UUID eventId,
        String eventType,
        String source,
        String aggregateId,
        Instant timestamp,
        Object payload
) {
}
