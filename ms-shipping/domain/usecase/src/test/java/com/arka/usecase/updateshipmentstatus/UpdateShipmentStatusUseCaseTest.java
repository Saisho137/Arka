package com.arka.usecase.updateshipmentstatus;

import com.arka.model.commons.exception.ShipmentNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateShipmentStatusUseCaseTest {

    @Mock private ShipmentRepository shipmentRepository;

    private UpdateShipmentStatusUseCase useCase;

    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new UpdateShipmentStatusUseCase(shipmentRepository);
    }

    @Test
    @DisplayName("Should update status to IN_TRANSIT")
    void shouldUpdateStatusToInTransit() {
        Shipment updated = buildShipment(ShippingStatus.IN_TRANSIT);
        when(shipmentRepository.updateStatus(eq(ORDER_ID), eq("IN_TRANSIT"), any()))
                .thenReturn(Mono.just(updated));

        StepVerifier.create(useCase.execute(ORDER_ID, "IN_TRANSIT", null))
                .expectNextMatches(s -> s.status() == ShippingStatus.IN_TRANSIT)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should set actualDeliveryDate when status is DELIVERED")
    void shouldSetDeliveryDateWhenDelivered() {
        Shipment delivered = buildShipment(ShippingStatus.DELIVERED);
        when(shipmentRepository.updateStatus(eq(ORDER_ID), eq("DELIVERED"), any(Instant.class)))
                .thenReturn(Mono.just(delivered));

        StepVerifier.create(useCase.execute(ORDER_ID, "DELIVERED", null))
                .expectNextMatches(s -> s.status() == ShippingStatus.DELIVERED)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw ShipmentNotFoundException when orderId not found")
    void shouldThrowWhenNotFound() {
        when(shipmentRepository.updateStatus(eq(ORDER_ID), eq("IN_TRANSIT"), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(ORDER_ID, "IN_TRANSIT", null))
                .expectError(ShipmentNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid status")
    void shouldThrowForInvalidStatus() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(ORDER_ID, "INVALID_STATUS", null));
    }

    private Shipment buildShipment(ShippingStatus status) {
        return Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(ORDER_ID)
                .trackingNumber("DHL-ABC123")
                .carrier(Carrier.DHL)
                .status(status)
                .deliveryAddress(new DeliveryAddress("Calle 100", "Bogotá", "Cundinamarca", "11001", "CO"))
                .build();
    }
}
