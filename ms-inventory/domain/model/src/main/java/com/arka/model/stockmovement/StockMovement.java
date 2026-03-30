package com.arka.model.stockmovement;

import com.arka.valueobjects.MovementType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder(toBuilder = true)
public record StockMovement(
        UUID movementId,
        UUID sku,
        UUID orderId,
        int quantity,
        MovementType type,
        LocalDateTime createdAt,
        String createdBy
) {
    public StockMovement {
        if (movementId == null) throw new IllegalArgumentException("MovementId is required");
        if (sku == null) throw new IllegalArgumentException("ProductId is required");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        if (type == null) throw new IllegalArgumentException("MovementType is required");
        if (createdAt == null) throw new IllegalArgumentException("CreatedAt is required");
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("CreatedBy is required for audit purposes");
        }
    }
}
