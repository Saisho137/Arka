package com.arka.usecase.eventtrace;

import com.arka.model.eventstore.EventStoreEntry;
import com.arka.model.eventstore.gateways.EventStoreRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class EventTraceUseCase {

    private final EventStoreRepository eventStoreRepository;

    public List<EventStoreEntry> traceByCorrelationId(UUID correlationId) {
        return eventStoreRepository.findByCorrelationId(correlationId);
    }
}

