package com.arka.model.supplier;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record Supplier(
        UUID id,
        String name,
        String email,
        String phone,
        String address,
        String country,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        List<SupplierProduct> products
) {
    public Supplier {
        country = country != null ? country : "CO";
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }
}
