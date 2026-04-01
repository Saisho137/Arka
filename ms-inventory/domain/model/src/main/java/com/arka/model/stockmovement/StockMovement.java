package com.arka.model.stockmovement;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record StockMovement(
        UUID id,
        String sku,
        MovementType movementType,
        int quantityChange,
        int previousQuantity,
        int newQuantity,
        UUID referenceId,
        String reason,
        Instant createdAt
) {
    public StockMovement {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(movementType, "movementType is required");
        if (previousQuantity < 0) throw new IllegalArgumentException("previousQuantity must be >= 0");
        if (newQuantity < 0) throw new IllegalArgumentException("newQuantity must be >= 0");
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public boolean isStockIncrease() {
        return quantityChange > 0;
    }

    public boolean isStockDecrease() {
        return quantityChange < 0;
    }
}
