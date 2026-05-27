package com.arka.usecase.retryshipment;

import com.arka.model.commons.exception.InvalidShipmentStateException;
import com.arka.model.commons.exception.ShipmentNotFoundException;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.shipment.*;
import com.arka.model.shipment.gateways.S3Storage;
import com.arka.model.shipment.gateways.ShipmentRepository;
import com.arka.model.shipment.gateways.ShippingCarrier;
import com.arka.model.shipment.gateways.ShippingCarrierFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryShipmentUseCaseTest {

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ShippingCarrierFactory shippingCarrierFactory;
    @Mock private S3Storage s3Storage;
    @Mock private ShippingCarrier shippingCarrier;

    private RetryShipmentUseCase useCase;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final DeliveryAddress ADDRESS = new DeliveryAddress(
            "Carrera 7 #32-16", "Medellín", "Antioquia", "05001", "CO");

    @BeforeEach
    void setUp() {
        useCase = new RetryShipmentUseCase(
                shipmentRepository, outboxEventRepository, shippingCarrierFactory, s3Storage);
    }

    @Test
    @DisplayName("Should throw ShipmentNotFoundException when shipment not found")
    void shouldThrowWhenNotFound() {
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(ORDER_ID))
                .expectError(ShipmentNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should throw InvalidShipmentStateException when shipment is not FAILED")
    void shouldThrowWhenNotFailed() {
        Shipment shipment = Shipment.builder()
                .orderId(ORDER_ID)
                .carrier(Carrier.FEDEX)
                .status(ShippingStatus.LABEL_GENERATED)
                .deliveryAddress(ADDRESS)
                .build();

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Mono.just(shipment));

        StepVerifier.create(useCase.execute(ORDER_ID))
                .expectError(InvalidShipmentStateException.class)
                .verify();
    }

    @Test
    @DisplayName("Should retry successfully and transition to LABEL_GENERATED")
    void shouldRetrySuccessfully() {
        Shipment failedShipment = Shipment.builder()
                .orderId(ORDER_ID)
                .carrier(Carrier.DHL)
                .status(ShippingStatus.FAILED)
                .deliveryAddress(ADDRESS)
                .failureReason("Previous failure")
                .build();

        ShippingResult successResult = ShippingResult.builder()
                .success(true)
                .trackingNumber("DHL-RETRY123")
                .labelPdf("retried label".getBytes())
                .estimatedDeliveryDate(Instant.now().plus(4, ChronoUnit.DAYS))
                .build();

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Mono.just(failedShipment));
        when(shippingCarrierFactory.getCarrier(Carrier.DHL)).thenReturn(shippingCarrier);
        when(shippingCarrier.generateLabel(ORDER_ID, ADDRESS)).thenReturn(Mono.just(successResult));
        when(s3Storage.uploadFile(any(byte[].class), anyString(), anyString()))
                .thenReturn(Mono.just("s3://bucket/label.pdf"));
        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenReturn(Mono.just(OutboxEvent.builder()
                        .eventType(com.arka.model.outboxevent.EventType.SHIPPING_DISPATCHED)
                        .payload("{}")
                        .partitionKey(ORDER_ID.toString())
                        .build()));

        StepVerifier.create(useCase.execute(ORDER_ID))
                .expectNextMatches(s ->
                        s.status() == ShippingStatus.LABEL_GENERATED &&
                                s.trackingNumber().equals("DHL-RETRY123") &&
                                s.failureReason() == null)
                .verifyComplete();

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should maintain FAILED status when retry also fails")
    void shouldMaintainFailedOnRetryFailure() {
        Shipment failedShipment = Shipment.builder()
                .orderId(ORDER_ID)
                .carrier(Carrier.FEDEX)
                .status(ShippingStatus.FAILED)
                .deliveryAddress(ADDRESS)
                .failureReason("Original failure")
                .build();

        ShippingResult failResult = ShippingResult.builder()
                .success(false)
                .reason("Carrier still down")
                .build();

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Mono.just(failedShipment));
        when(shippingCarrierFactory.getCarrier(Carrier.FEDEX)).thenReturn(shippingCarrier);
        when(shippingCarrier.generateLabel(ORDER_ID, ADDRESS)).thenReturn(Mono.just(failResult));
        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(ORDER_ID))
                .expectNextMatches(s ->
                        s.status() == ShippingStatus.FAILED &&
                                "Carrier still down".equals(s.failureReason()))
                .verifyComplete();

        verify(outboxEventRepository, never()).save(any());
    }
}
