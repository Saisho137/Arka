package com.arka.model.shipment;

import java.util.Objects;

public record DeliveryAddress(
        String street,
        String city,
        String state,
        String postalCode,
        String country
) {
    public DeliveryAddress {
        Objects.requireNonNull(street, "street is required");
        Objects.requireNonNull(city, "city is required");
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(postalCode, "postalCode is required");
        Objects.requireNonNull(country, "country is required");
        if (!"CO".equals(country)) {
            throw new IllegalArgumentException("Delivery outside Colombia not supported");
        }
        if (!postalCode.matches("\\d{5}")) {
            throw new IllegalArgumentException("Invalid postal code format");
        }
    }
}
