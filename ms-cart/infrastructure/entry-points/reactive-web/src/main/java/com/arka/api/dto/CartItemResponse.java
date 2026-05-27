package com.arka.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record CartItemResponse(
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        Instant addedAt
) {}
