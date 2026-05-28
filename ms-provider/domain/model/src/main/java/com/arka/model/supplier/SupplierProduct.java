package com.arka.model.supplier;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
public record SupplierProduct(
        UUID id,
        UUID supplierId,
        String sku,
        String supplierSku,
        BigDecimal unitPrice,
        int leadTimeDays,
        BigDecimal reorderMultiplier,
        boolean preferred,
        Instant createdAt
) {
    public SupplierProduct {
        leadTimeDays = leadTimeDays > 0 ? leadTimeDays : 7;
        reorderMultiplier = reorderMultiplier != null ? reorderMultiplier : new BigDecimal("2.0");
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
