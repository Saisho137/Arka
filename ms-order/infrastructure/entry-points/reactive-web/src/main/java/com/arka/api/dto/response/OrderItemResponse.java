package com.arka.api.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder(toBuilder = true)
public record OrderItemResponse(
    UUID id,
    UUID productId,
    String sku,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
}
