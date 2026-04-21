package com.arka.r2dbc.outbox;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SpringDataOutboxRepository extends ReactiveCrudRepository<OutboxEventDTO, UUID> {

    @Query("SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at LIMIT :limit")
    Flux<OutboxEventDTO> findPending(int limit);

    @Modifying
    @Query("UPDATE outbox_events SET status = 'PUBLISHED' WHERE id = :id")
    Mono<Integer> markAsPublished(UUID id);
}
