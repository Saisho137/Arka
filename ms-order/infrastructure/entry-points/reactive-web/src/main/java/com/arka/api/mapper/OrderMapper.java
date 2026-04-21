package com.arka.api.mapper;

import com.arka.api.dto.request.OrderItemRequest;
import com.arka.api.dto.response.OrderItemResponse;
import com.arka.api.dto.response.OrderResponse;
import com.arka.api.dto.response.OrderSummaryResponse;
import com.arka.model.order.Order;
import com.arka.model.order.OrderItem;
import com.arka.usecase.order.OrderUseCase;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
        // Utility class
    }

    // ──────────────────────────────────────────────────────────────────
    // Request → Command
    // ──────────────────────────────────────────────────────────────────

    public static List<OrderUseCase.OrderItemCommand> toOrderItemCommands(
            List<OrderItemRequest> requests) {
        return requests.stream()
                .map(req -> new OrderUseCase.OrderItemCommand(
                        req.productId(),
                        req.sku(),
                        req.quantity()))
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────
    // Domain → Response
    // ──────────────────────────────────────────────────────────────────

    public static OrderResponse toOrderResponse(Order order, List<OrderItem> items) {
        return OrderResponse.builder()
                .orderId(order.id())
                .customerId(order.customerId())
                .status(order.status().value())
                .totalAmount(order.totalAmount())
                .customerEmail(order.customerEmail())
                .shippingAddress(order.shippingAddress())
                .notes(order.notes())
                .items(toOrderItemResponses(items))
                .createdAt(order.createdAt())
                .build();
    }

    public static OrderResponse toOrderResponse(OrderUseCase.OrderWithItems orderWithItems) {
        return toOrderResponse(orderWithItems.order(), orderWithItems.items());
    }

    public static OrderSummaryResponse toOrderSummaryResponse(Order order) {
        return OrderSummaryResponse.builder()
                .orderId(order.id())
                .customerId(order.customerId())
                .status(order.status().value())
                .totalAmount(order.totalAmount())
                .createdAt(order.createdAt())
                .build();
    }

    public static List<OrderItemResponse> toOrderItemResponses(List<OrderItem> items) {
        return items.stream()
                .map(OrderMapper::toOrderItemResponse)
                .toList();
    }

    public static OrderItemResponse toOrderItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.id())
                .productId(item.productId())
                .sku(item.sku())
                .productName(item.productName())
                .quantity(item.quantity())
                .unitPrice(item.unitPrice())
                .subtotal(item.subtotal())
                .build();
    }
}
