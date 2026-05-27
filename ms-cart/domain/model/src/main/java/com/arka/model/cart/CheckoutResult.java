package com.arka.model.cart;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record CheckoutResult(
        UUID cartId,
        List<PriceChange> priceChanges,
        BigDecimal totalAmount,
        CheckoutStatus status
) {}
