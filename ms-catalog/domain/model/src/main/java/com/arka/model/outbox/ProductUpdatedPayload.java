package com.arka.model.outbox;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for ProductUpdated domain event.
 * Published when a product is updated or deactivated.
 * Note: productId and categoryId are UUIDs, but sku remains as String (natural identifier).
 */
@Builder(toBuilder = true)
public record ProductUpdatedPayload(
    UUID productId,
    String sku,
    String name,
    BigDecimal cost,
    BigDecimal price,
    String currency,
    UUID categoryId,
    boolean active
) {
}
