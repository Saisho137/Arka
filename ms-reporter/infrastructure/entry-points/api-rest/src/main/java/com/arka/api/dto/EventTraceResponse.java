package com.arka.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record EventTraceResponse(
        UUID correlationId,
        List<EventEntryResponse> events
) {
}
