package com.arka.model.shipment;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record Shipment(
        UUID id,
        UUID orderId,
        String trackingNumber,
        Carrier carrier,
        String shippingLabelUrl,
        ShippingStatus status,
        DeliveryAddress deliveryAddress,
        Instant estimatedDeliveryDate,
        Instant actualDeliveryDate,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public Shipment {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(carrier, "carrier is required");
        Objects.requireNonNull(deliveryAddress, "deliveryAddress is required");
        status = status != null ? status : ShippingStatus.PENDING;
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public boolean isPending() {
        return status == ShippingStatus.PENDING;
    }

    public boolean isLabelGenerated() {
        return status == ShippingStatus.LABEL_GENERATED;
    }

    public boolean isFailed() {
        return status == ShippingStatus.FAILED;
    }

    public boolean isDelivered() {
        return status == ShippingStatus.DELIVERED;
    }

    public boolean isInTransit() {
        return status == ShippingStatus.IN_TRANSIT;
    }
}
