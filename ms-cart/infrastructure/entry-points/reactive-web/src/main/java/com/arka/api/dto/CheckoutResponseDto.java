package com.arka.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record CheckoutResponseDto(
        UUID cartId,
        List<PriceChangeDto> priceChanges,
        BigDecimal totalAmount,
        String status
) {}
