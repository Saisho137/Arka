package com.arka.model.stock;

import com.arka.model.commons.exception.ExcessiveReleaseException;
import com.arka.model.commons.exception.InsufficientStockException;
import com.arka.model.commons.exception.InvalidStockQuantityException;
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

    // --- Métodos de consulta ---

    public boolean canReserve(int requestedQuantity) {
        return availableQuantity >= requestedQuantity;
    }

    public boolean isBelowThreshold(int threshold) {
        return availableQuantity <= threshold;
    }

    // --- Mutaciones encapsuladas ---

    public Stock increaseBy(int amount) {
        if (amount <= 0) throw new InvalidStockQuantityException(sku, amount, "must be > 0");
        return this.toBuilder()
                .quantity(this.quantity + amount)
                .updatedAt(Instant.now())
                .build();
    }

    public Stock decreaseBy(int amount) {
        if (amount <= 0) throw new InvalidStockQuantityException(sku, amount, "must be > 0");
        if (amount > availableQuantity) throw new InsufficientStockException(sku, amount, availableQuantity);
        return this.toBuilder()
                .quantity(this.quantity - amount)
                .updatedAt(Instant.now())
                .build();
    }

    public Stock setQuantity(int newQuantity) {
        if (newQuantity < 0) throw new InvalidStockQuantityException(sku, newQuantity, "must be >= 0");
        if (newQuantity < reservedQuantity)
            throw new InvalidStockQuantityException(sku, newQuantity, reservedQuantity);
        return this.toBuilder()
                .quantity(newQuantity)
                .updatedAt(Instant.now())
                .build();
    }

    public Stock reserve(int amount) {
        if (amount <= 0) throw new InvalidStockQuantityException(sku, amount, "must be > 0");
        if (amount > availableQuantity) throw new InsufficientStockException(sku, amount, availableQuantity);
        return this.toBuilder()
                .reservedQuantity(this.reservedQuantity + amount)
                .updatedAt(Instant.now())
                .build();
    }

    public Stock releaseReservation(int amount) {
        if (amount <= 0) throw new InvalidStockQuantityException(sku, amount, "must be > 0");
        if (amount > reservedQuantity)
            throw new ExcessiveReleaseException(sku, amount, reservedQuantity);
        return this.toBuilder()
                .reservedQuantity(this.reservedQuantity - amount)
                .updatedAt(Instant.now())
                .build();
    }
}
