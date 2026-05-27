package com.arka.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        UUID orderId,
        String trackingNumber,
        String carrier,
        String shippingLabelUrl,
        String status,
        DeliveryAddressDto deliveryAddress,
        Instant estimatedDeliveryDate,
        Instant actualDeliveryDate,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {}
