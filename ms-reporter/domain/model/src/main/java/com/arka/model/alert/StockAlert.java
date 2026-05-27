package com.arka.model.alert;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record StockAlert(
        UUID id,
        String sku,
        String productName,
        int currentStock,
        BigDecimal dailyRate,
        int daysUntilOut,
        AlertStatus alertStatus,
        Instant createdAt,
        Instant resolvedAt
) {
    public StockAlert {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (alertStatus == null) {
            alertStatus = AlertStatus.ACTIVE;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
