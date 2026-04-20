package com.arka.model.outboxevent;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record OrderConfirmedPayload(
    UUID orderId,
    UUID customerId,
    String customerEmail,
    List<OrderItemPayload> items,
    BigDecimal totalAmount
) {
}
