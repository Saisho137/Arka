package com.arka.r2dbc.processedevent;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("processed_events")
public record ProcessedEventDTO(
        @Id UUID id,
        @Column("processed_at") Instant processedAt
) {}
