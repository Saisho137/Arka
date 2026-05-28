package com.arka.r2dbc.supplier;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("suppliers")
@Builder(toBuilder = true)
public record SupplierEntity(
        @Id UUID id,
        String name,
        String email,
        String phone,
        String address,
        String country,
        boolean active,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {}
