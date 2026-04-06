package com.arka.r2dbc.stock;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("stock")
@Builder(toBuilder = true)
public record StockDTO(
        @Id UUID id,
        String sku,
        @Column("product_id") UUID productId,
        int quantity,
        @Column("reserved_quantity") int reservedQuantity,
        @Column("depletion_threshold") int depletionThreshold,
        @Column("updated_at") Instant updatedAt,
        long version
) {}
