package com.arka.r2dbc.supplier;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("supplier_products")
@Builder(toBuilder = true)
public record SupplierProductEntity(
        @Id UUID id,
        @Column("supplier_id") UUID supplierId,
        String sku,
        @Column("supplier_sku") String supplierSku,
        @Column("unit_price") BigDecimal unitPrice,
        @Column("lead_time_days") int leadTimeDays,
        @Column("reorder_multiplier") BigDecimal reorderMultiplier,
        boolean preferred,
        @Column("created_at") Instant createdAt
) {}
