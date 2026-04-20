package com.arka.model.order;

import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record ProductInfo(
    String sku,
    String productName,
    BigDecimal unitPrice
) {
}
