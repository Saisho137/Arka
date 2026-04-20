package com.arka.model.outboxevent;

import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record OrderItemPayload(
    String sku,
    int quantity,
    BigDecimal unitPrice
) {
}
