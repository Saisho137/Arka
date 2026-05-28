package com.arka.model.purchaseorder;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder(toBuilder = true)
public record PurchaseOrderItem(
        UUID id,
        UUID purchaseOrderId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
