package com.arka.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record StockAlertResponse(
        UUID id,
        String sku,
        String productName,
        int currentStock,
        BigDecimal dailyRate,
        int daysUntilOut,
        String alertStatus,
        Instant createdAt
) {
}
