package com.arka.model.outboxevent;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for ProductCreated domain event.
 * Published when a new product is created in the catalog.
 * Note: productId and categoryId are UUIDs, but sku remains as String (natural identifier).
 */
@Builder(toBuilder = true)
public record ProductCreatedPayload(
    UUID productId,
    String sku,
    String name,
    BigDecimal cost,
    BigDecimal price,
    String currency,
    UUID categoryId,
    int initialStock
) {
}
