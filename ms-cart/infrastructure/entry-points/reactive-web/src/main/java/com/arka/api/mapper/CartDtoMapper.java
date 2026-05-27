package com.arka.api.mapper;

import com.arka.api.dto.*;
import com.arka.model.cart.Cart;
import com.arka.model.cart.CartItem;
import com.arka.model.cart.CheckoutResult;
import com.arka.model.cart.PriceChange;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CartDtoMapper {

    public static CartResponse toResponse(Cart cart) {
        return CartResponse.builder()
                .cartId(cart.id())
                .customerId(cart.customerId())
                .items(cart.items().stream().map(CartDtoMapper::toCartItemResponse).toList())
                .status(cart.status().name())
                .createdAt(cart.createdAt())
                .lastModifiedAt(cart.lastModifiedAt())
                .build();
    }

    public static CartItemResponse toCartItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .sku(item.sku())
                .productName(item.productName())
                .quantity(item.quantity())
                .unitPrice(item.unitPrice())
                .addedAt(item.addedAt())
                .build();
    }

    public static CheckoutResponseDto toCheckoutResponse(CheckoutResult result) {
        return CheckoutResponseDto.builder()
                .cartId(result.cartId())
                .priceChanges(result.priceChanges().stream().map(CartDtoMapper::toPriceChangeDto).toList())
                .totalAmount(result.totalAmount())
                .status(result.status().name())
                .build();
    }

    public static PriceChangeDto toPriceChangeDto(PriceChange pc) {
        return PriceChangeDto.builder()
                .sku(pc.sku())
                .oldPrice(pc.oldPrice())
                .newPrice(pc.newPrice())
                .build();
    }
}
