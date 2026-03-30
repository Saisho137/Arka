package com.arka.model.stock;

import com.arka.valueobjects.WarehouseLocation;
import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record Stock(
        UUID sku,
        int availableQuantity,
        int reservedQuantity,
        int stockThreshold,
        WarehouseLocation warehouseLocation
) {
    public Stock {
        if (sku == null) throw new IllegalArgumentException("SKU is required");
        if (availableQuantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        if (reservedQuantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        if (stockThreshold < 0) throw new IllegalArgumentException("Stock threshold cannot be negative");
    }

    // Domain Logic Example: Calculate total physical stock
    public int getTotalPhysicalStock() {
        return availableQuantity + reservedQuantity;
    }
}