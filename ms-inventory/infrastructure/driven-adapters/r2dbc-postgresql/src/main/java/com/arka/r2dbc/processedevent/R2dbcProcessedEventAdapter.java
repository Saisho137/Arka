package com.arka.r2dbc.processedevent;

import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcProcessedEventAdapter implements ProcessedEventRepository {

    private final SpringDataProcessedEventRepository repository;

    @Override
    public Mono<Boolean> exists(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public Mono<Void> save(UUID eventId) {
        return repository.save(ProcessedEventDTO.builder()
                        .eventId(eventId)
                        .processedAt(Instant.now())
                        .build())
                .then();
    }
}
