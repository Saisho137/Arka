package com.arka.model.payment.gateways;

import com.arka.model.payment.event.OutboxEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OutboxEventRepository {
    Mono<OutboxEvent> save(OutboxEvent event);
    Flux<OutboxEvent> findPendingEvents();
    Mono<Void> markAsPublished(OutboxEvent event);
}
