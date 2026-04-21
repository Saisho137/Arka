package com.arka.model.order;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder(toBuilder = true)
public record ProductInfo(
    UUID productId,
    String sku,
    String productName,
    BigDecimal unitPrice
) {
}
