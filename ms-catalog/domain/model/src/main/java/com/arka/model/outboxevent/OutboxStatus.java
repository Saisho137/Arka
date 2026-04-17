package com.arka.model.outboxevent;

/**
 * OutboxStatus represents the publication status of an outbox event.
 * PENDING: Event has been inserted but not yet published to Kafka.
 * PUBLISHED: Event has been successfully published to Kafka.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
