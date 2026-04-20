package com.arka.model.order;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record Order(
    UUID id,
    UUID customerId,
    OrderStatus status,
    BigDecimal totalAmount,
    String customerEmail,
    String shippingAddress,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
    public Order {
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(customerEmail, "customerEmail is required");
        Objects.requireNonNull(shippingAddress, "shippingAddress is required");

        status    = status    != null ? status    : new OrderStatus.PendingReserve();
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }
}
