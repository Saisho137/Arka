package com.arka.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record ProductResponse(
    UUID id,
    String sku,
    String name,
    String description,
    BigDecimal cost,
    BigDecimal price,
    String currency,
    UUID categoryId,
    String categoryName,
    boolean active,
    List<ReviewResponse> reviews,
    Instant createdAt,
    Instant updatedAt
) {}
