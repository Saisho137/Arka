package com.arka.r2dbc.purchaseorder;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SpringDataPurchaseOrderRepository extends ReactiveCrudRepository<PurchaseOrderEntity, UUID> {

    @Modifying
    @Query("UPDATE purchase_orders SET status = :status, updated_at = :updatedAt, sent_at = :sentAt, confirmed_at = :confirmedAt, received_at = :receivedAt WHERE id = :id")
    Mono<Integer> updateStatus(UUID id, String status, java.time.Instant updatedAt,
                                java.time.Instant sentAt, java.time.Instant confirmedAt, java.time.Instant receivedAt);
}
