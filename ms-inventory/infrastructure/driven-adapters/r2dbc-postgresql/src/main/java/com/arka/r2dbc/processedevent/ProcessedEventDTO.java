package com.arka.r2dbc.processedevent;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("processed_events")
@Builder(toBuilder = true)
public record ProcessedEventDTO(
        @Id @Column("event_id") UUID eventId,
        @Column("processed_at") Instant processedAt
) {}
