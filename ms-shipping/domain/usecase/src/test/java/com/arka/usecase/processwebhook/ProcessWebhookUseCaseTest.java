package com.arka.usecase.processwebhook;

import com.arka.model.shipment.*;
import com.arka.model.shipment.gateways.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessWebhookUseCaseTest {

    @Mock private ShipmentRepository shipmentRepository;

    private ProcessWebhookUseCase useCase;

    private static final String TRACKING_NUMBER = "DHL-ABC12345";
    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ProcessWebhookUseCase(shipmentRepository);
    }

    @Test
    @DisplayName("Should update status when tracking number found")
    void shouldUpdateStatusWhenFound() {
        Shipment existing = buildShipment(ShippingStatus.LABEL_GENERATED);
        Shipment updated = buildShipment(ShippingStatus.IN_TRANSIT);

        when(shipmentRepository.findByTrackingNumber(TRACKING_NUMBER)).thenReturn(Mono.just(existing));
        when(shipmentRepository.updateStatus(eq(ORDER_ID), eq("IN_TRANSIT"), any())).thenReturn(Mono.just(updated));

        StepVerifier.create(useCase.execute(TRACKING_NUMBER, "IN_TRANSIT", null))
                .expectNextMatches(s -> s.status() == ShippingStatus.IN_TRANSIT)
                .verifyComplete();

        verify(shipmentRepository).updateStatus(eq(ORDER_ID), eq("IN_TRANSIT"), any());
    }

    @Test
    @DisplayName("Should set delivery date when status is DELIVERED")
    void shouldSetDeliveryDateWhenDelivered() {
        Shipment existing = buildShipment(ShippingStatus.IN_TRANSIT);
        Shipment delivered = buildShipment(ShippingStatus.DELIVERED);
        Instant deliveryDate = Instant.now();

        when(shipmentRepository.findByTrackingNumber(TRACKING_NUMBER)).thenReturn(Mono.just(existing));
        when(shipmentRepository.updateStatus(eq(ORDER_ID), eq("DELIVERED"), eq(deliveryDate)))
                .thenReturn(Mono.just(delivered));

        StepVerifier.create(useCase.execute(TRACKING_NUMBER, "DELIVERED", deliveryDate))
                .expectNextMatches(s -> s.status() == ShippingStatus.DELIVERED)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should complete empty when tracking number not found")
    void shouldCompleteEmptyWhenNotFound() {
        when(shipmentRepository.findByTrackingNumber(TRACKING_NUMBER)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(TRACKING_NUMBER, "IN_TRANSIT", null))
                .verifyComplete();

        verify(shipmentRepository, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw for invalid status value")
    void shouldThrowForInvalidStatus() {
        Shipment existing = buildShipment(ShippingStatus.LABEL_GENERATED);
        when(shipmentRepository.findByTrackingNumber(TRACKING_NUMBER)).thenReturn(Mono.just(existing));

        StepVerifier.create(useCase.execute(TRACKING_NUMBER, "NONEXISTENT", null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private Shipment buildShipment(ShippingStatus status) {
        return Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(ORDER_ID)
                .trackingNumber(TRACKING_NUMBER)
                .carrier(Carrier.DHL)
                .status(status)
                .deliveryAddress(new DeliveryAddress("Calle 100", "Bogotá", "Cundinamarca", "11001", "CO"))
                .build();
    }
}
