package com.arka.r2dbc.shipment;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface SpringDataShipmentRepository extends ReactiveCrudRepository<ShipmentDTO, UUID> {

    @Query("SELECT * FROM shipments WHERE order_id = :orderId LIMIT 1")
    Mono<ShipmentDTO> findByOrderId(UUID orderId);

    @Query("SELECT * FROM shipments WHERE tracking_number = :trackingNumber LIMIT 1")
    Mono<ShipmentDTO> findByTrackingNumber(String trackingNumber);

    @Modifying
    @Query("UPDATE shipments SET status = :status, actual_delivery_date = :actualDeliveryDate, updated_at = NOW() WHERE order_id = :orderId")
    Mono<Integer> updateStatus(UUID orderId, String status, Instant actualDeliveryDate);

    @Query("""
        SELECT * FROM shipments
        WHERE (:status IS NULL OR status = :status)
        AND (:carrier IS NULL OR carrier = :carrier)
        ORDER BY created_at DESC
        LIMIT :size OFFSET :offset
        """)
    Flux<ShipmentDTO> findByFilters(String status, String carrier, int size, int offset);
}
