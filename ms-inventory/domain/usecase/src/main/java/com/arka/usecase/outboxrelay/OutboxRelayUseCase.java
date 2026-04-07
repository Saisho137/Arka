package com.arka.usecase.outboxrelay;

import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class OutboxRelayUseCase {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(readOnly = true)
    public Flux<OutboxEvent> fetchPendingEvents() {
        return outboxEventRepository.findPending(BATCH_SIZE);
    }

    @Transactional
    public Mono<Void> markAsPublished(OutboxEvent event) {
        return outboxEventRepository.markAsPublished(event.id());
    }
}
