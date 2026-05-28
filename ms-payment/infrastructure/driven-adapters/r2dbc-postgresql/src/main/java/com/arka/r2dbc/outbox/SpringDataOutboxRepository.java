package com.arka.r2dbc.outbox;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SpringDataOutboxRepository extends R2dbcRepository<OutboxEventDTO, UUID> {
    @Query("SELECT * FROM outbox_events WHERE published = false ORDER BY created_at ASC LIMIT 50")
    Flux<OutboxEventDTO> findByPublishedFalseOrderByCreatedAtAsc();
}
