package com.arka.model.outboxevent;

import lombok.Builder;

@Builder(toBuilder = true)
public record StockDepletedPayload(
        String sku,
        int currentQuantity,
        int threshold
) {
}
