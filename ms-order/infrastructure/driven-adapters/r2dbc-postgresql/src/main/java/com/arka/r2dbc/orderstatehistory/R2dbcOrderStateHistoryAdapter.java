package com.arka.r2dbc.orderstatehistory;

import com.arka.model.order.OrderStateHistory;
import com.arka.model.order.gateways.OrderStateHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcOrderStateHistoryAdapter implements OrderStateHistoryRepository {

    private final SpringDataOrderStateHistoryRepository repository;
    private final DatabaseClient client;

    @Override
    public Mono<OrderStateHistory> save(OrderStateHistory history) {
        // Use DatabaseClient with explicit CAST because order_status is a PG enum
        // and previousStatus/newStatus are plain Strings (OrderStatus is a sealed interface).
        UUID id = history.id() != null ? history.id() : UUID.randomUUID();

        DatabaseClient.GenericExecuteSpec spec = client.sql("""
                        INSERT INTO order_state_history
                            (id, order_id, previous_status, new_status, changed_by, reason, created_at)
                        VALUES
                            (:id, :orderId,
                             CAST(:previousStatus AS order_status),
                             CAST(:newStatus AS order_status),
                             :changedBy, :reason, :createdAt)
                        """)
                .bind("id", id)
                .bind("orderId", history.orderId())
                .bind("newStatus", history.newStatus())
                .bind("createdAt", history.createdAt());

        if (history.previousStatus() != null) {
            spec = spec.bind("previousStatus", history.previousStatus());
        } else {
            spec = spec.bindNull("previousStatus", String.class);
        }
        if (history.changedBy() != null) {
            spec = spec.bind("changedBy", history.changedBy());
        } else {
            spec = spec.bindNull("changedBy", UUID.class);
        }
        if (history.reason() != null) {
            spec = spec.bind("reason", history.reason());
        } else {
            spec = spec.bindNull("reason", String.class);
        }

        return spec.then().thenReturn(history);
    }

    @Override
    public Flux<OrderStateHistory> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId)
                .map(OrderStateHistoryDTOMapper::toDomain);
    }
}
