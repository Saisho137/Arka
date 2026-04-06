package com.arka.r2dbc.stockmovement;

import com.arka.model.stockmovement.MovementType;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("stock_movements")
@Builder(toBuilder = true)
public record StockMovementDTO(
        @Id UUID id,
        String sku,
        @Column("movement_type") MovementType movementType,
        @Column("quantity_change") int quantityChange,
        @Column("previous_quantity") int previousQuantity,
        @Column("new_quantity") int newQuantity,
        @Column("order_id") UUID orderId,
        String reason,
        @Column("created_at") Instant createdAt
) {}
