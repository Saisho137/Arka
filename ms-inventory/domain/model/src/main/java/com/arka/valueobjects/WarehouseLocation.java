package com.arka.valueobjects;

import lombok.Builder;

@Builder
public record WarehouseLocation(String aisle, String shelf) {
    public WarehouseLocation {
        if (aisle == null || aisle.isBlank()) {
            throw new IllegalArgumentException("Aisle cannot be null or empty");
        }
        if (shelf == null || shelf.isBlank()) {
            throw new IllegalArgumentException("Shelf cannot be null or empty");
        }
    }
}