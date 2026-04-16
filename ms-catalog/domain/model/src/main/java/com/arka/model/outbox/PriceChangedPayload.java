package com.arka.model.outbox;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for PriceChanged domain event.
 * Published when a product's price is updated, in addition to ProductUpdated event.
 * Note: productId is UUID, but sku remains as String (natural identifier).
 */
@Builder(toBuilder = true)
public record PriceChangedPayload(
    UUID productId,
    String sku,
    BigDecimal oldPrice,
    BigDecimal newPrice,
    String currency
) {
}
