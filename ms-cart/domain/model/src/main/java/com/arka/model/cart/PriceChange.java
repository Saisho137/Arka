package com.arka.model.cart;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PriceChange(
        String sku,
        BigDecimal oldPrice,
        BigDecimal newPrice
) {}
