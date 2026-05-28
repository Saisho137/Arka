package com.arka.r2dbc.processedevent;

import com.arka.model.payment.gateways.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class R2dbcProcessedEventAdapter implements ProcessedEventRepository {

    private final SpringDataProcessedEventRepository repository;

    @Override
    public Mono<Boolean> exists(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public Mono<Void> save(UUID eventId) {
        ProcessedEventDTO dto = new ProcessedEventDTO(eventId, Instant.now());
        return repository.save(dto).then();
    }
}
