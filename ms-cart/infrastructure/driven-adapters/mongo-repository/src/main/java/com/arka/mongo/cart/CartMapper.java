package com.arka.mongo.cart;

import com.arka.model.cart.Cart;
import com.arka.model.cart.CartItem;
import com.arka.model.cart.CartStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CartMapper {

    public static CartDocument toDocument(Cart cart) {
        return CartDocument.builder()
                .id(cart.id())
                .customerId(cart.customerId())
                .items(cart.items().stream().map(CartItemDocument::fromDomain).toList())
                .status(cart.status().name())
                .createdAt(cart.createdAt())
                .lastModifiedAt(cart.lastModifiedAt())
                .build();
    }

    public static Cart toDomain(CartDocument doc) {
        return Cart.builder()
                .id(doc.getId())
                .customerId(doc.getCustomerId())
                .items(doc.getItems() == null ? List.of() : doc.getItems().stream().map(CartMapper::toCartItem).toList())
                .status(CartStatus.valueOf(doc.getStatus()))
                .createdAt(doc.getCreatedAt())
                .lastModifiedAt(doc.getLastModifiedAt())
                .build();
    }

    private static CartItem toCartItem(CartItemDocument doc) {
        return CartItem.builder()
                .sku(doc.getSku())
                .productName(doc.getProductName())
                .quantity(doc.getQuantity())
                .unitPrice(doc.getUnitPrice())
                .addedAt(doc.getAddedAt())
                .build();
    }
}
