package com.arka.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreatePurchaseOrderRequest(
        String supplierId,
        List<ItemRequest> items,
        String notes
) {
    public record ItemRequest(String sku, int quantity) {}
}
