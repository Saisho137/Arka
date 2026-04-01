package com.arka.model.outboxevent;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record StockReserveFailedPayload(
        String sku,
        UUID orderId,
        int requestedQuantity,
        int availableQuantity,
        String reason
) {
}
