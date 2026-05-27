package com.arka.api.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record TopSellingProductResponse(
        String sku,
        String productName,
        int totalQuantity,
        BigDecimal totalRevenue
) {
}
