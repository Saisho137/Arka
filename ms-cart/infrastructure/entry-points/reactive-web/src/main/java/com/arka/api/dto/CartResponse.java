package com.arka.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record CartResponse(
        UUID cartId,
        String customerId,
        List<CartItemResponse> items,
        String status,
        Instant createdAt,
        Instant lastModifiedAt
) {}
