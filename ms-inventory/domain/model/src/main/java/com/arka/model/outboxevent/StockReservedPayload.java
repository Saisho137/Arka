package com.arka.model.outboxevent;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record StockReservedPayload(
        String sku,
        UUID orderId,
        int quantity,
        UUID reservationId
) {
}
