package com.arka.usecase.getshipment;

import com.arka.model.commons.exception.ShipmentNotFoundException;
import com.arka.model.shipment.Shipment;
import com.arka.model.shipment.gateways.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class GetShipmentUseCase {

    private final ShipmentRepository shipmentRepository;

    public Mono<Shipment> execute(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new ShipmentNotFoundException(orderId.toString())));
    }
}
