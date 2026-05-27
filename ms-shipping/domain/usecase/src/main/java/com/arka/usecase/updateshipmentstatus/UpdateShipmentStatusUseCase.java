package com.arka.usecase.updateshipmentstatus;

import com.arka.model.commons.exception.ShipmentNotFoundException;
import com.arka.model.shipment.Shipment;
import com.arka.model.shipment.ShippingStatus;
import com.arka.model.shipment.gateways.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class UpdateShipmentStatusUseCase {

    private final ShipmentRepository shipmentRepository;

    public Mono<Shipment> execute(UUID orderId, String newStatus, Instant actualDeliveryDate) {
        ShippingStatus.fromValue(newStatus); // validates
        Instant deliveryDate = "DELIVERED".equalsIgnoreCase(newStatus) ? Instant.now() : actualDeliveryDate;
        return shipmentRepository.updateStatus(orderId, newStatus.toUpperCase(), deliveryDate)
                .switchIfEmpty(Mono.error(new ShipmentNotFoundException(orderId.toString())))
                .doOnSuccess(s -> log.info("Updated shipment status for orderId={} to {}", orderId, newStatus));
    }
}
