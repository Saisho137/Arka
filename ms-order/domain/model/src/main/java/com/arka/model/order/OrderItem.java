package com.arka.model.order;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record OrderItem(
    UUID id,
    UUID orderId,
    UUID productId,
    String sku,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
    public OrderItem {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(sku, "sku is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        subtotal = unitPrice != null
            ? unitPrice.multiply(BigDecimal.valueOf(quantity))
            : BigDecimal.ZERO;
    }
}
