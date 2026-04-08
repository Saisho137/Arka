package com.arka.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record StockResponse(
        UUID id,
        String sku,
        UUID productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        long version,
        Instant updatedAt
) {}
