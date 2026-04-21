package com.arka.r2dbc.order;

import com.arka.model.order.Order;
import com.arka.model.order.OrderStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OrderDTOMapper {

    static Order toDomain(OrderDTO data) {
        return Order.builder()
                .id(data.id())
                .customerId(data.customerId())
                .status(OrderStatus.fromValue(data.status()))
                .totalAmount(data.totalAmount())
                .customerEmail(data.customerEmail())
                .shippingAddress(data.shippingAddress())
                .notes(data.notes())
                .createdAt(data.createdAt())
                .updatedAt(data.updatedAt())
                .build();
    }

    static OrderDTO toDTO(Order domain) {
        return OrderDTO.builder()
                .id(domain.id())
                .customerId(domain.customerId())
                .status(domain.status().value())
                .totalAmount(domain.totalAmount())
                .customerEmail(domain.customerEmail())
                .shippingAddress(domain.shippingAddress())
                .notes(domain.notes())
                .createdAt(domain.createdAt())
                .updatedAt(domain.updatedAt())
                .build();
    }
}
