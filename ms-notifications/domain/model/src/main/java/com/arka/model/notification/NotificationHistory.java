package com.arka.model.notification;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record NotificationHistory(
        UUID id,
        String eventId,
        String eventType,
        String recipientEmail,
        String subject,
        NotificationStatus status,
        String errorMessage,
        Instant processedAt
) {
    public NotificationHistory {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(recipientEmail, "recipientEmail is required");
        Objects.requireNonNull(status, "status is required");
        id = id != null ? id : UUID.randomUUID();
        processedAt = processedAt != null ? processedAt : Instant.now();
    }
}
