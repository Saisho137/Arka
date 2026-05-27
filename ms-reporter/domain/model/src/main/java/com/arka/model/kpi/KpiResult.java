package com.arka.model.kpi;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
public record KpiResult(
        BigDecimal totalRevenue,
        long totalOrders,
        BigDecimal averageOrderValue,
        BigDecimal conversionRate,
        BigDecimal cartAbandonmentRate,
        BigDecimal averageDeliveryTimeDays,
        List<TopSellingProduct> topSellingProducts
) {
}
