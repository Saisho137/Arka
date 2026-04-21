package com.arka.r2dbc.orderitem;

import com.arka.model.order.OrderItem;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OrderItemDTOMapper {

    static OrderItem toDomain(OrderItemDTO data, java.math.BigDecimal subtotal) {
        return OrderItem.builder()
                .id(data.id())
                .orderId(data.orderId())
                .productId(data.productId())
                .sku(data.sku())
                .productName(data.productName())
                .quantity(data.quantity())
                .unitPrice(data.unitPrice())
                .subtotal(subtotal)
                .build();
    }

    static OrderItemDTO toDTO(OrderItem domain) {
        return OrderItemDTO.builder()
                .id(domain.id())
                .orderId(domain.orderId())
                .productId(domain.productId())
                .sku(domain.sku())
                .productName(domain.productName())
                .quantity(domain.quantity())
                .unitPrice(domain.unitPrice())
                // subtotal is not included as it's a generated column
                .build();
    }
}
