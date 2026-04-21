package com.arka.api.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record OrderSummaryResponse(
    UUID orderId,
    UUID customerId,
    String status,
    BigDecimal totalAmount,
    Instant createdAt
) {
}
