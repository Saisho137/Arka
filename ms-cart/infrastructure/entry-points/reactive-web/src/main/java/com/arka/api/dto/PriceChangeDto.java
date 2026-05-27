package com.arka.api.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PriceChangeDto(String sku, BigDecimal oldPrice, BigDecimal newPrice) {}
