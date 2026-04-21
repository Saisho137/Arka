package com.arka.r2dbc.orderstatehistory;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("order_state_history")
@Builder(toBuilder = true)
public record OrderStateHistoryDTO(
        @Id UUID id,
        @Column("order_id") UUID orderId,
        @Column("previous_status") String previousStatus,
        @Column("new_status") String newStatus,
        @Column("changed_by") UUID changedBy,
        String reason,
        @Column("created_at") Instant createdAt
) {}
