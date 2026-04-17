package com.arka.model.outboxevent.gateways;

import com.arka.model.outboxevent.OutboxEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OutboxEventRepository {

    Mono<OutboxEvent> save(OutboxEvent event);

    Flux<OutboxEvent> findPending(int limit);

    Mono<Void> markAsPublished(UUID eventId);
}
