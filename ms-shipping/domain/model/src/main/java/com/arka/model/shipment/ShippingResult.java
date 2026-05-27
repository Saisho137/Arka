package com.arka.model.shipment;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;

@Builder(toBuilder = true)
public record ShippingResult(
        boolean success,
        String trackingNumber,
        byte[] labelPdf,
        Instant estimatedDeliveryDate,
        String reason
) {
    public ShippingResult {
        if (success) {
            Objects.requireNonNull(trackingNumber, "trackingNumber is required for successful result");
            Objects.requireNonNull(labelPdf, "labelPdf is required for successful result");
        } else {
            Objects.requireNonNull(reason, "reason is required for failed result");
        }
    }
}
