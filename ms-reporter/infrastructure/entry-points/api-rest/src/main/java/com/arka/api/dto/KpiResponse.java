package com.arka.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder(toBuilder = true)
public record KpiResponse(
        BigDecimal totalRevenue,
        long totalOrders,
        BigDecimal averageOrderValue,
        BigDecimal conversionRate,
        BigDecimal cartAbandonmentRate,
        BigDecimal averageDeliveryTimeDays,
        List<TopSellingProductResponse> topSellingProducts
) {
}
