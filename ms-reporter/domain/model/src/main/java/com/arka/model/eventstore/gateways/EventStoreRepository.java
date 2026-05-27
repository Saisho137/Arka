package com.arka.model.eventstore.gateways;

import com.arka.model.eventstore.EventStoreEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventStoreRepository {

    void save(EventStoreEntry entry);

    List<EventStoreEntry> findByCorrelationId(UUID correlationId);

    List<EventStoreEntry> findByEventTypeAndTimestampRange(String eventType, Instant from, Instant to);

    long countByEventTypeAndTimestampRange(String eventType, Instant from, Instant to);

    List<EventStoreEntry> findAllOrderedByTimestamp(int offset, int limit);
}
