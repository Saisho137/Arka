package com.arka.model.payment.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record OutboxEvent(
        UUID id,
        EventType eventType,
        String payload,
        String partitionKey,
        boolean published,
        Instant createdAt
) {}
