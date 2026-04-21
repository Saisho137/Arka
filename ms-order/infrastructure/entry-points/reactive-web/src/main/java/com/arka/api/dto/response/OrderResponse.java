package com.arka.api.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record OrderResponse(
    UUID orderId,
    UUID customerId,
    String status,
    BigDecimal totalAmount,
    String customerEmail,
    String shippingAddress,
    String notes,
    List<OrderItemResponse> items,
    Instant createdAt
) {
}
