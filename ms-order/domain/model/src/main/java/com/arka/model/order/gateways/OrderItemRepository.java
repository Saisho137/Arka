package com.arka.model.order.gateways;

import com.arka.model.order.OrderItem;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository {

    Flux<OrderItem> saveAll(List<OrderItem> items);

    Flux<OrderItem> findByOrderId(UUID orderId);
}
