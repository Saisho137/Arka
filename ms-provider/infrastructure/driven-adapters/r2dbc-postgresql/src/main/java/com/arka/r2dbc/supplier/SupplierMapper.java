package com.arka.r2dbc.supplier;

import com.arka.model.supplier.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SupplierMapper {

    static Supplier toDomain(SupplierEntity entity) {
        return Supplier.builder()
                .id(entity.id())
                .name(entity.name())
                .email(entity.email())
                .phone(entity.phone())
                .address(entity.address())
                .country(entity.country())
                .active(entity.active())
                .createdAt(entity.createdAt())
                .updatedAt(entity.updatedAt())
                .build();
    }

    static SupplierEntity toEntity(Supplier domain) {
        return SupplierEntity.builder()
                .id(domain.id())
                .name(domain.name())
                .email(domain.email())
                .phone(domain.phone())
                .address(domain.address())
                .country(domain.country())
                .active(domain.active())
                .createdAt(domain.createdAt())
                .updatedAt(domain.updatedAt())
                .build();
    }
}
