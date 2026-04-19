package com.arka.model.idempotency;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record IdempotencyRecord(
        UUID idempotencyKey,
        Instant createdAt
) {
    public IdempotencyRecord {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
