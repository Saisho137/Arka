package com.arka.model.order;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record ReserveStockResult(
    boolean success,
    UUID reservationId,
    int availableQuantity,
    String reason
) {
}
