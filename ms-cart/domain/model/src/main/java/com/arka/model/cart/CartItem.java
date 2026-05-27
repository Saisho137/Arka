package com.arka.model.cart;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Builder
public record CartItem(
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        Instant addedAt
) {
    public CartItem {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(productName, "productName is required");
        Objects.requireNonNull(unitPrice, "unitPrice is required");
        Objects.requireNonNull(addedAt, "addedAt is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("unitPrice must be >= 0");
        }
    }
}
