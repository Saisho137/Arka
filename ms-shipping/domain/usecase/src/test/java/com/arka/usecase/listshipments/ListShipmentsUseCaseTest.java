package com.arka.usecase.listshipments;

import com.arka.model.shipment.*;
import com.arka.model.shipment.gateways.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListShipmentsUseCaseTest {

    @Mock private ShipmentRepository shipmentRepository;

    private ListShipmentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListShipmentsUseCase(shipmentRepository);
    }

    @Test
    @DisplayName("Should return filtered shipments with pagination")
    void shouldReturnFilteredShipments() {
        Shipment shipment = buildShipment(ShippingStatus.IN_TRANSIT, Carrier.DHL);
        when(shipmentRepository.findByFilters("IN_TRANSIT", "DHL", 0, 20))
                .thenReturn(Flux.just(shipment));

        StepVerifier.create(useCase.execute("IN_TRANSIT", "DHL", 0, 20))
                .expectNextMatches(s -> s.status() == ShippingStatus.IN_TRANSIT && s.carrier() == Carrier.DHL)
                .verifyComplete();

        verify(shipmentRepository).findByFilters("IN_TRANSIT", "DHL", 0, 20);
    }

    @Test
    @DisplayName("Should return all shipments when no filters applied")
    void shouldReturnAllWhenNoFilters() {
        Shipment s1 = buildShipment(ShippingStatus.PENDING, Carrier.DHL);
        Shipment s2 = buildShipment(ShippingStatus.DELIVERED, Carrier.FEDEX);
        when(shipmentRepository.findByFilters(null, null, 0, 20))
                .thenReturn(Flux.just(s1, s2));

        StepVerifier.create(useCase.execute(null, null, 0, 20))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty flux when no shipments match filters")
    void shouldReturnEmptyWhenNoMatch() {
        when(shipmentRepository.findByFilters("DELIVERED", "LEGACY", 0, 10))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("DELIVERED", "LEGACY", 0, 10))
                .verifyComplete();
    }

    private Shipment buildShipment(ShippingStatus status, Carrier carrier) {
        return Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .trackingNumber("TRK-" + UUID.randomUUID().toString().substring(0, 8))
                .carrier(carrier)
                .status(status)
                .deliveryAddress(new DeliveryAddress("Calle 100", "Bogotá", "Cundinamarca", "11001", "CO"))
                .build();
    }
}
