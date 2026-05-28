package com.arka.r2dbc.outbox;

import com.arka.model.outbox.EventType;
import com.arka.model.outbox.OutboxStatus;
import io.r2dbc.postgresql.codec.Json;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("outbox_events")
@Builder(toBuilder = true)
public record OutboxEventEntity(
        @Id UUID id,
        @Column("event_type") String eventType,
        String topic,
        @Column("partition_key") String partitionKey,
        Json payload,
        String status,
        @Column("created_at") Instant createdAt
) {}
