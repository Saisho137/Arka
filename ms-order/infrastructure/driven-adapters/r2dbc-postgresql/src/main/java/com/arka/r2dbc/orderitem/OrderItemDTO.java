package com.arka.r2dbc.orderitem;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Table("order_items")
@Builder(toBuilder = true)
public record OrderItemDTO(
        @Id UUID id,
        @Column("order_id") UUID orderId,
        @Column("product_id") UUID productId,
        String sku,
        @Column("product_name") String productName,
        int quantity,
        @Column("unit_price") BigDecimal unitPrice
        // Note: subtotal is a GENERATED column in PostgreSQL, so we don't include it in the DTO for inserts
) {}
