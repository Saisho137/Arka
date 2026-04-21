package com.arka.r2dbc.orderitem;

import com.arka.model.order.OrderItem;
import com.arka.model.order.gateways.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcOrderItemAdapter implements OrderItemRepository {

    private final SpringDataOrderItemRepository repository;
    private final DatabaseClient client;

    @Override
    public Flux<OrderItem> saveAll(List<OrderItem> items) {
        return Flux.fromIterable(items)
                .map(OrderItemDTOMapper::toDTO)
                .flatMap(repository::save)
                .flatMap(dto -> 
                    // After saving, we need to fetch the generated subtotal
                    client.sql("SELECT id, order_id, product_id, sku, product_name, quantity, unit_price, subtotal " +
                            "FROM order_items WHERE id = :id")
                            .bind("id", dto.id())
                            .map((row, metadata) -> OrderItemDTOMapper.toDomain(
                                    dto,
                                    row.get("subtotal", BigDecimal.class)
                            ))
                            .one()
                );
    }

    @Override
    public Flux<OrderItem> findByOrderId(UUID orderId) {
        return client.sql("SELECT id, order_id, product_id, sku, product_name, quantity, unit_price, subtotal " +
                        "FROM order_items WHERE order_id = :orderId")
                .bind("orderId", orderId)
                .map((row, metadata) -> OrderItemDTOMapper.toDomain(
                        OrderItemDTO.builder()
                                .id(row.get("id", UUID.class))
                                .orderId(row.get("order_id", UUID.class))
                                .productId(row.get("product_id", UUID.class))
                                .sku(row.get("sku", String.class))
                                .productName(row.get("product_name", String.class))
                                .quantity(row.get("quantity", Integer.class))
                                .unitPrice(row.get("unit_price", BigDecimal.class))
                                .build(),
                        row.get("subtotal", BigDecimal.class)
                ))
                .all();
    }
}
