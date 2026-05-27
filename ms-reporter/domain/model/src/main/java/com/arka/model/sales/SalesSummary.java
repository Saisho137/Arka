package com.arka.model.sales;

import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

@Builder(toBuilder = true)
public record SalesSummary(
        String sku,
        LocalDate weekStartDate,
        int totalOrders,
        int totalQuantity,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue,
        String productName,
        Instant lastUpdatedAt
) {
    public SalesSummary {
        if (lastUpdatedAt == null) {
            lastUpdatedAt = Instant.now();
        }
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }
        if (averageOrderValue == null && totalOrders > 0) {
            averageOrderValue = totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
        } else if (averageOrderValue == null) {
            averageOrderValue = BigDecimal.ZERO;
        }
    }
}
