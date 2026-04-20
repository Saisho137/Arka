package com.arka.model.order.gateways;

import com.arka.model.order.OrderStateHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OrderStateHistoryRepository {

    Mono<OrderStateHistory> save(OrderStateHistory history);

    Flux<OrderStateHistory> findByOrderId(UUID orderId);
}
