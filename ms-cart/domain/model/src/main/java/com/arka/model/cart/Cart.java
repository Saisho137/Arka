package com.arka.model.cart;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record Cart(
        UUID id,
        String customerId,
        List<CartItem> items,
        CartStatus status,
        Instant createdAt,
        Instant lastModifiedAt
) {
    public Cart {
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt is required");
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean isCheckedOut() {
        return status == CartStatus.CHECKED_OUT;
    }

    public boolean isAbandoned() {
        return status == CartStatus.ABANDONED;
    }

    public BigDecimal calculateTotal() {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
