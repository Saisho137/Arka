package com.arka.model.kpi;

import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record TopSellingProduct(
        String sku,
        String productName,
        int totalQuantity,
        BigDecimal totalRevenue
) {
}
