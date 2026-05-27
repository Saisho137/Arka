package com.arka.r2dbc.shipment;

import com.arka.model.shipment.Shipment;
import com.arka.model.shipment.gateways.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcShipmentAdapter implements ShipmentRepository {

    private final SpringDataShipmentRepository repository;

    @Override
    public Mono<Shipment> save(Shipment shipment) {
        return repository.save(ShipmentDTOMapper.toDTO(shipment))
                .map(ShipmentDTOMapper::toDomain);
    }

    @Override
    public Mono<Shipment> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId)
                .map(ShipmentDTOMapper::toDomain);
    }

    @Override
    public Mono<Shipment> findByTrackingNumber(String trackingNumber) {
        return repository.findByTrackingNumber(trackingNumber)
                .map(ShipmentDTOMapper::toDomain);
    }

    @Override
    public Mono<Shipment> updateStatus(UUID orderId, String newStatus, Instant actualDeliveryDate) {
        return repository.updateStatus(orderId, newStatus, actualDeliveryDate)
                .flatMap(rows -> rows > 0 ? repository.findByOrderId(orderId).map(ShipmentDTOMapper::toDomain) : Mono.empty());
    }

    @Override
    public Flux<Shipment> findByFilters(String status, String carrier, int page, int size) {
        int offset = page * size;
        return repository.findByFilters(status, carrier, size, offset)
                .map(ShipmentDTOMapper::toDomain);
    }
}
