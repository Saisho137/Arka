package com.arka.usecase.processwebhook;

import com.arka.model.shipment.Shipment;
import com.arka.model.shipment.ShippingStatus;
import com.arka.model.shipment.gateways.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class ProcessWebhookUseCase {

    private final ShipmentRepository shipmentRepository;

    public Mono<Shipment> execute(String trackingNumber, String newStatus, Instant deliveryDate) {
        return shipmentRepository.findByTrackingNumber(trackingNumber)
                .flatMap(shipment -> {
                    ShippingStatus status = ShippingStatus.fromValue(newStatus);
                    Instant actualDate = status == ShippingStatus.DELIVERED ? deliveryDate : null;
                    return shipmentRepository.updateStatus(shipment.orderId(), status.name(), actualDate);
                })
                .doOnNext(s -> log.info("Webhook updated tracking={} to status={}", trackingNumber, newStatus))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Webhook received for unknown trackingNumber={}", trackingNumber);
                    return Mono.empty();
                }));
    }
}
