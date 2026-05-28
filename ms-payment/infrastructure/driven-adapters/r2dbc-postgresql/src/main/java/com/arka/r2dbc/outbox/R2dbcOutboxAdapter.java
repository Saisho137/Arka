package com.arka.r2dbc.outbox;

import com.arka.model.payment.event.EventType;
import com.arka.model.payment.event.OutboxEvent;
import com.arka.model.payment.gateways.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class R2dbcOutboxAdapter implements OutboxEventRepository {

    private final SpringDataOutboxRepository repository;

    @Override
    public Mono<OutboxEvent> save(OutboxEvent event) {
        OutboxEventDTO dto = new OutboxEventDTO(
                UUID.randomUUID(),
                event.eventType().value(),
                event.payload(),
                event.partitionKey(),
                false,
                Instant.now()
        );
        return repository.save(dto).map(this::toDomain);
    }

    @Override
    public Flux<OutboxEvent> findPendingEvents() {
        return repository.findByPublishedFalseOrderByCreatedAtAsc()
                .map(this::toDomain);
    }

    @Override
    public Mono<Void> markAsPublished(OutboxEvent event) {
        return repository.findById(event.id())
                .flatMap(dto -> {
                    OutboxEventDTO published = new OutboxEventDTO(
                            dto.id(), dto.eventType(), dto.payload(),
                            dto.partitionKey(), true, dto.createdAt()
                    );
                    return repository.save(published);
                })
                .then();
    }

    private OutboxEvent toDomain(OutboxEventDTO dto) {
        EventType eventType = resolveEventType(dto.eventType());
        return OutboxEvent.builder()
                .id(dto.id())
                .eventType(eventType)
                .payload(dto.payload())
                .partitionKey(dto.partitionKey())
                .published(dto.published())
                .createdAt(dto.createdAt())
                .build();
    }

    private EventType resolveEventType(String value) {
        for (EventType et : EventType.values()) {
            if (et.value().equals(value)) {
                return et;
            }
        }
        return EventType.PAYMENT_PROCESSED;
    }
}
