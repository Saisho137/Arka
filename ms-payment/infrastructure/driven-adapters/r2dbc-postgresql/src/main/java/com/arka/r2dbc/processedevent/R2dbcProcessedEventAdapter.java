package com.arka.r2dbc.processedevent;

import com.arka.model.payment.gateways.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class R2dbcProcessedEventAdapter implements ProcessedEventRepository {

    private final SpringDataProcessedEventRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    @Override
    public Mono<Boolean> exists(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public Mono<Void> save(UUID eventId) {
        ProcessedEventDTO dto = new ProcessedEventDTO(eventId, Instant.now());
        return entityTemplate.insert(dto).then();
    }
}
