package com.arka.r2dbc.processedevent;

import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class R2dbcProcessedEventAdapter implements ProcessedEventRepository {

    private final SpringDataProcessedEventRepository repository;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<Boolean> exists(UUID eventId) {
        return repository.existsById(eventId)
                .doOnNext(exists -> log.debug("EventId {} exists: {}", eventId, exists));
    }

    @Override
    public Mono<Void> save(UUID eventId) {
        return databaseClient.sql("INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, NOW())")
                .bind("eventId", eventId)
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> log.info("Successfully inserted processed event: eventId={}, rows={}", eventId, rows))
                .doOnError(error -> log.error("Failed to insert processed event: eventId={}, error={}", 
                        eventId, error.getMessage(), error))
                .then();
    }
}
