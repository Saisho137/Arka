package com.arka.model.shipment;

public enum Carrier {
    DHL,
    FEDEX,
    LEGACY;

    public static Carrier fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Carrier value cannot be null or blank");
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid carrier: '" + value + "'. Valid values: DHL, FEDEX, LEGACY");
        }
    }
}
