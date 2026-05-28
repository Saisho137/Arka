package com.arka.r2dbc.purchaseorder;

import com.arka.model.purchaseorder.PurchaseOrder;
import com.arka.model.purchaseorder.PurchaseOrderItem;
import com.arka.model.purchaseorder.PurchaseOrderStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PurchaseOrderMapper {

    static PurchaseOrder toDomain(PurchaseOrderEntity entity) {
        return PurchaseOrder.builder()
                .id(entity.id())
                .supplierId(entity.supplierId())
                .status(PurchaseOrderStatus.valueOf(entity.status()))
                .totalAmount(entity.totalAmount())
                .notes(entity.notes())
                .createdAt(entity.createdAt())
                .updatedAt(entity.updatedAt())
                .sentAt(entity.sentAt())
                .confirmedAt(entity.confirmedAt())
                .receivedAt(entity.receivedAt())
                .build();
    }

    static PurchaseOrderEntity toEntity(PurchaseOrder domain) {
        return PurchaseOrderEntity.builder()
                .id(domain.id())
                .supplierId(domain.supplierId())
                .status(domain.status().name())
                .totalAmount(domain.totalAmount())
                .notes(domain.notes())
                .createdAt(domain.createdAt())
                .updatedAt(domain.updatedAt())
                .sentAt(domain.sentAt())
                .confirmedAt(domain.confirmedAt())
                .receivedAt(domain.receivedAt())
                .build();
    }

    static PurchaseOrderItem itemToDomain(PurchaseOrderItemEntity entity) {
        return PurchaseOrderItem.builder()
                .id(entity.id())
                .purchaseOrderId(entity.purchaseOrderId())
                .sku(entity.sku())
                .productName(entity.productName())
                .quantity(entity.quantity())
                .unitPrice(entity.unitPrice())
                .subtotal(entity.subtotal())
                .build();
    }

    static PurchaseOrderItemEntity itemToEntity(PurchaseOrderItem domain) {
        return PurchaseOrderItemEntity.builder()
                .id(domain.id())
                .purchaseOrderId(domain.purchaseOrderId())
                .sku(domain.sku())
                .productName(domain.productName())
                .quantity(domain.quantity())
                .unitPrice(domain.unitPrice())
                .subtotal(domain.subtotal())
                .build();
    }
}
