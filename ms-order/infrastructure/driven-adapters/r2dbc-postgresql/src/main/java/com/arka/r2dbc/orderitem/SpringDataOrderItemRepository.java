package com.arka.r2dbc.orderitem;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SpringDataOrderItemRepository extends ReactiveCrudRepository<OrderItemDTO, UUID> {

    @Query("SELECT id, order_id, product_id, sku, product_name, quantity, unit_price, subtotal " +
            "FROM order_items WHERE order_id = :orderId")
    Flux<OrderItemDTO> findByOrderId(UUID orderId);
}
