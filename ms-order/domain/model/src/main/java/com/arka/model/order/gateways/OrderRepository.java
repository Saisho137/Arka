package com.arka.model.order.gateways;

import com.arka.model.order.Order;
import com.arka.model.order.OrderStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrderRepository {

    Mono<Order> save(Order order);

    Mono<Order> findById(UUID id);

    Mono<Order> updateStatus(UUID id, OrderStatus newStatus);

    Flux<Order> findByFilters(OrderStatus status, UUID customerId, int page, int size);

    Mono<Long> countByFilters(OrderStatus status, UUID customerId);
}
