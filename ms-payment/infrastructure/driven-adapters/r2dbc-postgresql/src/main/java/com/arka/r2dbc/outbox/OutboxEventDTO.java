package com.arka.r2dbc.outbox;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("outbox_events")
public record OutboxEventDTO(
        @Id UUID id,
        @Column("event_type") String eventType,
        String payload,
        @Column("partition_key") String partitionKey,
        boolean published,
        @Column("created_at") Instant createdAt
) {}
