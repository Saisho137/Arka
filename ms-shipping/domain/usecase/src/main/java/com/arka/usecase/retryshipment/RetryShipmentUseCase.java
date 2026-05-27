package com.arka.usecase.retryshipment;

import com.arka.model.commons.exception.InvalidShipmentStateException;
import com.arka.model.commons.exception.ShipmentNotFoundException;
import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.shipment.*;
import com.arka.model.shipment.gateways.S3Storage;
import com.arka.model.shipment.gateways.ShipmentRepository;
import com.arka.model.shipment.gateways.ShippingCarrierFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class RetryShipmentUseCase {

    private final ShipmentRepository shipmentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ShippingCarrierFactory shippingCarrierFactory;
    private final S3Storage s3Storage;

    public Mono<Shipment> execute(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .switchIfEmpty(Mono.error(new ShipmentNotFoundException(orderId.toString())))
                .flatMap(shipment -> {
                    if (!shipment.isFailed()) {
                        return Mono.error(new InvalidShipmentStateException(
                                "Only FAILED shipments can be retried. Current status: " + shipment.status()));
                    }
                    return retryLabelGeneration(shipment);
                });
    }

    private Mono<Shipment> retryLabelGeneration(Shipment shipment) {
        return shippingCarrierFactory.getCarrier(shipment.carrier())
                .generateLabel(shipment.orderId(), shipment.deliveryAddress())
                .flatMap(result -> {
                    if (result.success()) {
                        return handleRetrySuccess(shipment, result);
                    } else {
                        return handleRetryFailure(shipment, result.reason());
                    }
                });
    }

    private Mono<Shipment> handleRetrySuccess(Shipment shipment, ShippingResult result) {
        String s3Key = shipment.orderId() + "/" + result.trackingNumber() + ".pdf";
        return s3Storage.uploadFile(result.labelPdf(), s3Key, "application/pdf")
                .flatMap(labelUrl -> {
                    Shipment updated = shipment.toBuilder()
                            .trackingNumber(result.trackingNumber())
                            .shippingLabelUrl(labelUrl)
                            .status(ShippingStatus.LABEL_GENERATED)
                            .estimatedDeliveryDate(result.estimatedDeliveryDate())
                            .failureReason(null)
                            .updatedAt(Instant.now())
                            .build();
                    return shipmentRepository.save(updated)
                            .flatMap(saved -> {
                                String payload = buildPayload(saved);
                                OutboxEvent event = OutboxEvent.builder()
                                        .eventType(EventType.SHIPPING_DISPATCHED)
                                        .payload(payload)
                                        .partitionKey(saved.orderId().toString())
                                        .build();
                                return outboxEventRepository.save(event).thenReturn(saved);
                            });
                })
                .doOnSuccess(s -> log.info("Retry successful for orderId={}, tracking={}", shipment.orderId(), result.trackingNumber()));
    }

    private Mono<Shipment> handleRetryFailure(Shipment shipment, String reason) {
        Shipment updated = shipment.toBuilder()
                .failureReason(reason)
                .updatedAt(Instant.now())
                .build();
        return shipmentRepository.save(updated)
                .doOnSuccess(s -> log.warn("Retry FAILED for orderId={}: {}", shipment.orderId(), reason));
    }

    private String buildPayload(Shipment shipment) {
        return "{\"orderId\":\"" + shipment.orderId() + "\","
                + "\"trackingNumber\":\"" + shipment.trackingNumber() + "\","
                + "\"carrier\":\"" + shipment.carrier() + "\","
                + "\"estimatedDeliveryDate\":\"" + shipment.estimatedDeliveryDate() + "\","
                + "\"shippingLabelUrl\":\"" + shipment.shippingLabelUrl() + "\","
                + "\"timestamp\":\"" + Instant.now() + "\"}";
    }
}
