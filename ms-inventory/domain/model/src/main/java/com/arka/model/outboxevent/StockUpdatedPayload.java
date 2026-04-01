package com.arka.model.outboxevent;

import lombok.Builder;

@Builder(toBuilder = true)
public record StockUpdatedPayload(
        String sku,
        int previousQuantity,
        int newQuantity,
        String movementType
) {
}
