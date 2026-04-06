package com.arka.r2dbc.outbox;

import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxStatus;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("outbox_events")
@Builder(toBuilder = true)
public record OutboxEventDTO(
        @Id UUID id,
        @Column("event_type") EventType eventType,
        String payload,
        @Column("partition_key") String partitionKey,
        OutboxStatus status,
        @Column("created_at") Instant createdAt
) {}
