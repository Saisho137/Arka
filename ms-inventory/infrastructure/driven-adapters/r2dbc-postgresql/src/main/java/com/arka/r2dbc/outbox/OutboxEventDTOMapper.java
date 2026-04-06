package com.arka.r2dbc.outbox;

import com.arka.model.outboxevent.OutboxEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OutboxEventDTOMapper {

    static OutboxEvent toDomain(OutboxEventDTO data) {
        return OutboxEvent.builder()
                .id(data.id())
                .eventType(data.eventType())
                .payload(data.payload())
                .partitionKey(data.partitionKey())
                .status(data.status())
                .createdAt(data.createdAt())
                .build();
    }

    static OutboxEventDTO toDTO(OutboxEvent domain) {
        return OutboxEventDTO.builder()
                .id(domain.id())
                .eventType(domain.eventType())
                .payload(domain.payload())
                .partitionKey(domain.partitionKey())
                .status(domain.status())
                .createdAt(domain.createdAt())
                .build();
    }
}
