package com.arka.r2dbc.purchaseorder;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Table("purchase_order_items")
@Builder(toBuilder = true)
public record PurchaseOrderItemEntity(
        @Id UUID id,
        @Column("purchase_order_id") UUID purchaseOrderId,
        String sku,
        @Column("product_name") String productName,
        int quantity,
        @Column("unit_price") BigDecimal unitPrice,
        BigDecimal subtotal
) {}
