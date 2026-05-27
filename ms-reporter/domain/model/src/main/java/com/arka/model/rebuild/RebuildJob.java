package com.arka.model.rebuild;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record RebuildJob(
        UUID id,
        String readModel,
        RebuildStatus status,
        long eventsProcessed,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {
    public RebuildJob {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = RebuildStatus.IN_PROGRESS;
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
