package com.arka.usecase.listshipments;

import com.arka.model.shipment.Shipment;
import com.arka.model.shipment.gateways.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ListShipmentsUseCase {

    private final ShipmentRepository shipmentRepository;

    public Flux<Shipment> execute(String status, String carrier, int page, int size) {
        return shipmentRepository.findByFilters(status, carrier, page, size);
    }
}
