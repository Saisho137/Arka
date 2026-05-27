package com.arka.usecase.processshipment;

import com.arka.model.commons.exception.InvalidDeliveryAddressException;
import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.shipment.*;
import com.arka.model.shipment.gateways.S3Storage;
import com.arka.model.shipment.gateways.ShipmentRepository;
import com.arka.model.shipment.gateways.ShippingCarrier;
import com.arka.model.shipment.gateways.ShippingCarrierFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ProcessShipmentUseCase {

    private final ShipmentRepository shipmentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ShippingCarrierFactory shippingCarrierFactory;
    private final S3Storage s3Storage;

    public Mono<Void> execute(UUID eventId, UUID orderId, DeliveryAddress deliveryAddress, Carrier carrier) {
        return processedEventRepository.exists(eventId)
                .flatMap(exists -> {
                    if (exists) {
                        log.debug("Event {} already processed — skipping", eventId);
                        return Mono.empty();
                    }
                    return processShipment(eventId, orderId, deliveryAddress, carrier);
                });
    }

    private Mono<Void> processShipment(UUID eventId, UUID orderId, DeliveryAddress deliveryAddress, Carrier carrier) {
        return validateAddress(deliveryAddress)
                .then(generateLabelWithCarrier(orderId, deliveryAddress, carrier))
                .flatMap(result -> {
                    if (result.success()) {
                        return handleSuccess(eventId, orderId, carrier, deliveryAddress, result);
                    } else {
                        return handleFailure(orderId, carrier, deliveryAddress, result.reason());
                    }
                })
                .onErrorResume(InvalidDeliveryAddressException.class, ex -> {
                    log.warn("Invalid address for orderId={}: {}", orderId, ex.getMessage());
                    return handleFailure(orderId, carrier, deliveryAddress, ex.getMessage());
                });
    }

    private Mono<Void> validateAddress(DeliveryAddress address) {
        try {
            // DeliveryAddress compact constructor validates everything
            new DeliveryAddress(address.street(), address.city(), address.state(), address.postalCode(), address.country());
            return Mono.empty();
        } catch (IllegalArgumentException | NullPointerException e) {
            return Mono.error(new InvalidDeliveryAddressException(e.getMessage()));
        }
    }

    private Mono<ShippingResult> generateLabelWithCarrier(UUID orderId, DeliveryAddress address, Carrier carrier) {
        ShippingCarrier shippingCarrier = shippingCarrierFactory.getCarrier(carrier);
        return shippingCarrier.generateLabel(orderId, address);
    }

    private Mono<Void> handleSuccess(UUID eventId, UUID orderId, Carrier carrier, DeliveryAddress address, ShippingResult result) {
        String s3Key = orderId + "/" + result.trackingNumber() + ".pdf";
        return s3Storage.uploadFile(result.labelPdf(), s3Key, "application/pdf")
                .flatMap(labelUrl -> {
                    Shipment shipment = Shipment.builder()
                            .orderId(orderId)
                            .trackingNumber(result.trackingNumber())
                            .carrier(carrier)
                            .shippingLabelUrl(labelUrl)
                            .status(ShippingStatus.LABEL_GENERATED)
                            .deliveryAddress(address)
                            .estimatedDeliveryDate(result.estimatedDeliveryDate())
                            .build();
                    return shipmentRepository.save(shipment)
                            .flatMap(saved -> {
                                String payload = buildShippingDispatchedPayload(saved);
                                OutboxEvent outboxEvent = OutboxEvent.builder()
                                        .eventType(EventType.SHIPPING_DISPATCHED)
                                        .payload(payload)
                                        .partitionKey(orderId.toString())
                                        .build();
                                return outboxEventRepository.save(outboxEvent);
                            })
                            .then(processedEventRepository.save(eventId))
                            .doOnSuccess(v -> log.info("Shipment created for orderId={}, tracking={}", orderId, result.trackingNumber()));
                });
    }

    private Mono<Void> handleFailure(UUID orderId, Carrier carrier, DeliveryAddress address, String reason) {
        Shipment shipment = Shipment.builder()
                .orderId(orderId)
                .carrier(carrier)
                .status(ShippingStatus.FAILED)
                .deliveryAddress(address)
                .failureReason(reason)
                .build();
        return shipmentRepository.save(shipment)
                .doOnSuccess(s -> log.warn("Shipment FAILED for orderId={}: {}", orderId, reason))
                .then();
    }

    private String buildShippingDispatchedPayload(Shipment shipment) {
        return "{\"orderId\":\"" + shipment.orderId() + "\","
                + "\"trackingNumber\":\"" + shipment.trackingNumber() + "\","
                + "\"carrier\":\"" + shipment.carrier() + "\","
                + "\"estimatedDeliveryDate\":\"" + shipment.estimatedDeliveryDate() + "\","
                + "\"shippingLabelUrl\":\"" + shipment.shippingLabelUrl() + "\","
                + "\"timestamp\":\"" + Instant.now() + "\"}";
    }
}
