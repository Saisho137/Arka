package com.arka.model.purchaseorder;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record PurchaseOrder(
        UUID id,
        UUID supplierId,
        PurchaseOrderStatus status,
        BigDecimal totalAmount,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt,
        Instant confirmedAt,
        Instant receivedAt,
        List<PurchaseOrderItem> items
) {
    public PurchaseOrder {
        status = status != null ? status : PurchaseOrderStatus.PENDING;
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }
}
