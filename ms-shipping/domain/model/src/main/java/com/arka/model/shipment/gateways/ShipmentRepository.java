package com.arka.model.shipment.gateways;

import com.arka.model.shipment.Shipment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface ShipmentRepository {

    Mono<Shipment> save(Shipment shipment);

    Mono<Shipment> findByOrderId(UUID orderId);

    Mono<Shipment> findByTrackingNumber(String trackingNumber);

    Mono<Shipment> updateStatus(UUID orderId, String newStatus, Instant actualDeliveryDate);

    Flux<Shipment> findByFilters(String status, String carrier, int page, int size);
}
