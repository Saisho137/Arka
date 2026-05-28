package com.arka.r2dbc.purchaseorder;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("purchase_orders")
@Builder(toBuilder = true)
public record PurchaseOrderEntity(
        @Id UUID id,
        @Column("supplier_id") UUID supplierId,
        String status,
        @Column("total_amount") BigDecimal totalAmount,
        String notes,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt,
        @Column("sent_at") Instant sentAt,
        @Column("confirmed_at") Instant confirmedAt,
        @Column("received_at") Instant receivedAt
) {}
