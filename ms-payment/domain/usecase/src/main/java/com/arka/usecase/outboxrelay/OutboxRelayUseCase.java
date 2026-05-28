package com.arka.usecase.outboxrelay;

import com.arka.model.payment.event.OutboxEvent;
import com.arka.model.payment.gateways.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class OutboxRelayUseCase {

    private final OutboxEventRepository outboxEventRepository;

    public Flux<OutboxEvent> fetchPendingEvents() {
        return outboxEventRepository.findPendingEvents();
    }

    public Mono<Void> markAsPublished(OutboxEvent event) {
        return outboxEventRepository.markAsPublished(event);
    }
}
