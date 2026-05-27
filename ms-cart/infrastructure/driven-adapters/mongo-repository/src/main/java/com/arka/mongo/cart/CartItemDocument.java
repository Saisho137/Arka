package com.arka.mongo.cart;

import com.arka.model.cart.CartItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class CartItemDocument {
    private String sku;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private Instant addedAt;

    public static CartItemDocument fromDomain(CartItem item) {
        return CartItemDocument.builder()
                .sku(item.sku())
                .productName(item.productName())
                .quantity(item.quantity())
                .unitPrice(item.unitPrice())
                .addedAt(item.addedAt())
                .build();
    }
}
