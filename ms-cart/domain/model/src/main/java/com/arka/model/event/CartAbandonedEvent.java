package com.arka.model.event;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record CartAbandonedEvent(
        UUID cartId,
        String customerId,
        int itemCount,
        BigDecimal totalAmount,
        Instant abandonedAt,
        Instant lastModifiedAt
) {}
