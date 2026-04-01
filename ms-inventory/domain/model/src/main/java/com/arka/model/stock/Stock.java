package com.arka.model.stock;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record Stock(
        UUID id,
        String sku,
        UUID productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        Instant updatedAt,
        long version
) {
    public Stock {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(productId, "productId is required");
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
        if (reservedQuantity < 0) throw new IllegalArgumentException("reservedQuantity must be >= 0");
        if (reservedQuantity > quantity)
            throw new IllegalArgumentException("reservedQuantity cannot exceed quantity");
        availableQuantity = quantity - reservedQuantity;
        version = version > 0 ? version : 1;
    }

    public int getTotalPhysicalStock() {
        return quantity;
    }

    public boolean canReserve(int requestedQuantity) {
        return availableQuantity >= requestedQuantity;
    }

    public boolean isBelowThreshold(int threshold) {
        return availableQuantity <= threshold;
    }

    public boolean canUpdateQuantityTo(int newQuantity) {
        return newQuantity >= reservedQuantity;
    }
}
