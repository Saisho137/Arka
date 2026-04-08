package com.arka.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record StockMovementResponse(
        UUID id,
        String sku,
        String movementType,
        int quantityChange,
        int previousQuantity,
        int newQuantity,
        UUID referenceId,
        String reason,
        Instant createdAt
) {}
