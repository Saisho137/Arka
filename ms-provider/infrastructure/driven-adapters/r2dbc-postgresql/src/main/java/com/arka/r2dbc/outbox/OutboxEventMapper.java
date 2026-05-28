package com.arka.r2dbc.outbox;

import com.arka.model.outbox.EventType;
import com.arka.model.outbox.OutboxEvent;
import com.arka.model.outbox.OutboxStatus;
import io.r2dbc.postgresql.codec.Json;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OutboxEventMapper {

    static OutboxEvent toDomain(OutboxEventEntity entity) {
        return OutboxEvent.builder()
                .id(entity.id())
                .eventType(EventType.valueOf(entity.eventType()))
                .topic(entity.topic())
                .partitionKey(entity.partitionKey())
                .payload(entity.payload() != null ? entity.payload().asString() : null)
                .status(OutboxStatus.valueOf(entity.status()))
                .createdAt(entity.createdAt())
                .build();
    }

    static OutboxEventEntity toEntity(OutboxEvent domain) {
        return OutboxEventEntity.builder()
                .id(domain.id())
                .eventType(domain.eventType().name())
                .topic(domain.topic())
                .partitionKey(domain.partitionKey())
                .payload(domain.payload() != null ? Json.of(domain.payload()) : null)
                .status(domain.status().name())
                .createdAt(domain.createdAt())
                .build();
    }
}
