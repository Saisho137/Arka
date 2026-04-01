package com.arka.model.outboxevent;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record StockReleasedPayload(
        String sku,
        UUID orderId,
        int quantity,
        String reason
) {
}
