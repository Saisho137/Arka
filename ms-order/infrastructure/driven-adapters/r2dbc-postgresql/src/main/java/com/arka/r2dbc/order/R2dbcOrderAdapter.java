package com.arka.r2dbc.order;

import com.arka.model.order.Order;
import com.arka.model.order.OrderStatus;
import com.arka.model.order.gateways.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcOrderAdapter implements OrderRepository {

    private final SpringDataOrderRepository repository;
    private final DatabaseClient client;

    @Override
    public Mono<Order> save(Order order) {
        return repository.save(OrderDTOMapper.toDTO(order))
                .map(OrderDTOMapper::toDomain);
    }

    @Override
    public Mono<Order> findById(UUID id) {
        return repository.findById(id)
                .map(OrderDTOMapper::toDomain);
    }

    @Override
    public Mono<Order> updateStatus(UUID id, OrderStatus newStatus) {
        return client.sql("UPDATE orders SET status = :status, updated_at = :updatedAt " +
                        "WHERE id = :id")
                .bind("status", newStatus.value())
                .bind("updatedAt", Instant.now())
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then(findById(id));
    }

    @Override
    public Flux<Order> findByFilters(OrderStatus status, UUID customerId, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT id, customer_id, status, total_amount, " +
                "customer_email, shipping_address, notes, created_at, updated_at " +
                "FROM orders WHERE 1=1");

        if (status != null) {
            sql.append(" AND status = :status");
        }
        if (customerId != null) {
            sql.append(" AND customer_id = :customerId");
        }

        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");

        DatabaseClient.GenericExecuteSpec spec = client.sql(sql.toString());

        if (status != null) {
            spec = spec.bind("status", status.value());
        }
        if (customerId != null) {
            spec = spec.bind("customerId", customerId);
        }

        spec = spec.bind("limit", size)
                .bind("offset", page * size);

        return spec.map((row, metadata) -> OrderDTO.builder()
                        .id(row.get("id", UUID.class))
                        .customerId(row.get("customer_id", UUID.class))
                        .status(row.get("status", String.class))
                        .totalAmount(row.get("total_amount", java.math.BigDecimal.class))
                        .customerEmail(row.get("customer_email", String.class))
                        .shippingAddress(row.get("shipping_address", String.class))
                        .notes(row.get("notes", String.class))
                        .createdAt(row.get("created_at", Instant.class))
                        .updatedAt(row.get("updated_at", Instant.class))
                        .build())
                .all()
                .map(OrderDTOMapper::toDomain);
    }

    @Override
    public Mono<Long> countByFilters(OrderStatus status, UUID customerId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM orders WHERE 1=1");

        if (status != null) {
            sql.append(" AND status = :status");
        }
        if (customerId != null) {
            sql.append(" AND customer_id = :customerId");
        }

        DatabaseClient.GenericExecuteSpec spec = client.sql(sql.toString());

        if (status != null) {
            spec = spec.bind("status", status.value());
        }
        if (customerId != null) {
            spec = spec.bind("customerId", customerId);
        }

        return spec.map((row, metadata) -> row.get(0, Long.class))
                .one();
    }
}
