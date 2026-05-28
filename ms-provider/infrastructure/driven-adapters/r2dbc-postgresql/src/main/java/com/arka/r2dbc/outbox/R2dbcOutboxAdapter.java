package com.arka.r2dbc.outbox;

import com.arka.model.gateways.OutboxEventRepository;
import com.arka.model.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcOutboxAdapter implements OutboxEventRepository {

    private final SpringDataOutboxRepository repository;

    @Override
    public Mono<OutboxEvent> save(OutboxEvent event) {
        return repository.save(OutboxEventMapper.toEntity(event))
                .map(OutboxEventMapper::toDomain);
    }

    @Override
    public Flux<OutboxEvent> findPending(int limit) {
        return repository.findPending(limit)
                .map(OutboxEventMapper::toDomain);
    }

    @Override
    public Mono<Void> markAsPublished(UUID id) {
        return repository.markAsPublished(id).then();
    }
}
