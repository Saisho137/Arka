package com.arka.model.shipment;

public enum ShippingStatus {
    PENDING,
    LABEL_GENERATED,
    IN_TRANSIT,
    DELIVERED,
    FAILED;

    public static ShippingStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ShippingStatus value cannot be null or blank");
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid shipping status: '" + value + "'. Valid values: PENDING, LABEL_GENERATED, IN_TRANSIT, DELIVERED, FAILED");
        }
    }
}
