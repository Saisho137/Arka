package com.arka.usecase.getshipment;

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

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetShipmentUseCaseTest {

    @Mock private ShipmentRepository shipmentRepository;

    private GetShipmentUseCase useCase;

    private static final UUID ORDER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetShipmentUseCase(shipmentRepository);
    }

    @Test
    @DisplayName("Should return shipment when found by orderId")
    void shouldReturnShipmentWhenFound() {
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(ORDER_ID)
                .trackingNumber("DHL-ABC123")
                .carrier(Carrier.DHL)
                .status(ShippingStatus.LABEL_GENERATED)
                .deliveryAddress(new DeliveryAddress("Calle 100", "Bogotá", "Cundinamarca", "11001", "CO"))
                .estimatedDeliveryDate(Instant.now())
                .build();

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Mono.just(shipment));

        StepVerifier.create(useCase.execute(ORDER_ID))
                .expectNextMatches(s -> s.orderId().equals(ORDER_ID) && s.trackingNumber().equals("DHL-ABC123"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw ShipmentNotFoundException when not found")
    void shouldThrowWhenNotFound() {
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(ORDER_ID))
                .expectError(ShipmentNotFoundException.class)
                .verify();
    }
}
