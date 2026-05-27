package com.arka.api;

import com.arka.api.dto.ShipmentResponse;
import com.arka.api.dto.UpdateStatusRequest;
import com.arka.model.shipment.*;
import com.arka.usecase.getshipment.GetShipmentUseCase;
import com.arka.usecase.listshipments.ListShipmentsUseCase;
import com.arka.usecase.retryshipment.RetryShipmentUseCase;
import com.arka.usecase.updateshipmentstatus.UpdateShipmentStatusUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@WebFluxTest(ApiRest.class)
class ShipmentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean private GetShipmentUseCase getShipmentUseCase;
    @MockitoBean private ListShipmentsUseCase listShipmentsUseCase;
    @MockitoBean private UpdateShipmentStatusUseCase updateShipmentStatusUseCase;
    @MockitoBean private RetryShipmentUseCase retryShipmentUseCase;

    private static final UUID ORDER_ID = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/shipments/{orderId} — 200 when found")
    void getShipment_shouldReturn200() {
        Shipment shipment = buildShipment(ShippingStatus.IN_TRANSIT);
        when(getShipmentUseCase.execute(ORDER_ID)).thenReturn(Mono.just(shipment));

        webTestClient.get()
                .uri("/api/v1/shipments/{orderId}", ORDER_ID)
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ShipmentResponse.class)
                .value(r -> {
                    assert r.orderId().equals(ORDER_ID);
                    assert r.status().equals("IN_TRANSIT");
                });
    }

    @Test
    @DisplayName("GET /api/v1/shipments — 403 when not ADMIN")
    void listShipments_shouldReturn403WhenNotAdmin() {
        webTestClient.get()
                .uri("/api/v1/shipments")
                .header("X-User-Role", "CUSTOMER")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("GET /api/v1/shipments — 200 when ADMIN")
    void listShipments_shouldReturn200WhenAdmin() {
        Shipment s = buildShipment(ShippingStatus.PENDING);
        when(listShipmentsUseCase.execute(any(), any(), eq(0), eq(20)))
                .thenReturn(Flux.just(s));

        webTestClient.get()
                .uri("/api/v1/shipments?page=0&size=20")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("PUT /api/v1/shipments/{orderId}/status — 403 when not ADMIN")
    void updateStatus_shouldReturn403WhenNotAdmin() {
        webTestClient.put()
                .uri("/api/v1/shipments/{orderId}/status", ORDER_ID)
                .header("X-User-Role", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateStatusRequest("IN_TRANSIT", null, null))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("PUT /api/v1/shipments/{orderId}/status — 200 when ADMIN")
    void updateStatus_shouldReturn200WhenAdmin() {
        Shipment updated = buildShipment(ShippingStatus.IN_TRANSIT);
        when(updateShipmentStatusUseCase.execute(eq(ORDER_ID), eq("IN_TRANSIT"), any()))
                .thenReturn(Mono.just(updated));

        webTestClient.put()
                .uri("/api/v1/shipments/{orderId}/status", ORDER_ID)
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateStatusRequest("IN_TRANSIT", null, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ShipmentResponse.class)
                .value(r -> {
                    assert r.status().equals("IN_TRANSIT");
                });
    }

    @Test
    @DisplayName("POST /api/v1/shipments/retry/{orderId} — 403 when not ADMIN")
    void retryShipment_shouldReturn403WhenNotAdmin() {
        webTestClient.post()
                .uri("/api/v1/shipments/retry/{orderId}", ORDER_ID)
                .header("X-User-Role", "CUSTOMER")
                .exchange()
                .expectStatus().isForbidden();
    }

    private Shipment buildShipment(ShippingStatus status) {
        return Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(ORDER_ID)
                .trackingNumber("DHL-TEST123")
                .carrier(Carrier.DHL)
                .status(status)
                .shippingLabelUrl("https://s3.example.com/label.pdf")
                .deliveryAddress(new DeliveryAddress("Calle 100", "Bogotá", "Cundinamarca", "11001", "CO"))
                .estimatedDeliveryDate(Instant.now())
                .build();
    }
}
