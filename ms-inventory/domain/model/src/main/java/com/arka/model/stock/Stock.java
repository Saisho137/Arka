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
        int depletionThreshold,
        Instant updatedAt,
        long version
) {
    public static final int DEFAULT_DEPLETION_THRESHOLD = 10;

    public Stock {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(productId, "productId is required");
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
        if (reservedQuantity < 0) throw new IllegalArgumentException("reservedQuantity must be >= 0");
        if (reservedQuantity > quantity)
            throw new IllegalArgumentException("reservedQuantity cannot exceed quantity");
        if (depletionThreshold < 0) throw new IllegalArgumentException("depletionThreshold must be >= 0");
        // id is nullable — DB generates UUID via DEFAULT gen_random_uuid()
        availableQuantity = quantity - reservedQuantity;
        depletionThreshold = depletionThreshold > 0 ? depletionThreshold : DEFAULT_DEPLETION_THRESHOLD;
        version = version > 0 ? version : 1;
    }

    // --- Métodos de consulta ---

    public boolean canReserve(int requestedQuantity) {
        return availableQuantity >= requestedQuantity;
    }

    public boolean isBelowThreshold() {
        return availableQuantity <= depletionThreshold;
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
