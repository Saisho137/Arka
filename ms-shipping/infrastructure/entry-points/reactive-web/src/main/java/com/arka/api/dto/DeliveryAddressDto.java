package com.arka.api.dto;

public record DeliveryAddressDto(
        String street,
        String city,
        String state,
        String postalCode,
        String country
) {}
