package com.arka.r2dbc.supplier;

import com.arka.model.supplier.SupplierProduct;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SupplierProductMapper {

    static SupplierProduct toDomain(SupplierProductEntity entity) {
        return SupplierProduct.builder()
                .id(entity.id())
                .supplierId(entity.supplierId())
                .sku(entity.sku())
                .supplierSku(entity.supplierSku())
                .unitPrice(entity.unitPrice())
                .leadTimeDays(entity.leadTimeDays())
                .reorderMultiplier(entity.reorderMultiplier())
                .preferred(entity.preferred())
                .createdAt(entity.createdAt())
                .build();
    }

    static SupplierProductEntity toEntity(SupplierProduct domain) {
        return SupplierProductEntity.builder()
                .id(domain.id())
                .supplierId(domain.supplierId())
                .sku(domain.sku())
                .supplierSku(domain.supplierSku())
                .unitPrice(domain.unitPrice())
                .leadTimeDays(domain.leadTimeDays())
                .reorderMultiplier(domain.reorderMultiplier())
                .preferred(domain.preferred())
                .createdAt(domain.createdAt())
                .build();
    }
}
