package com.arka.api;

import com.arka.model.shipment.Shipment;
import com.arka.usecase.getshipment.GetShipmentUseCase;
import com.arka.usecase.listshipments.ListShipmentsUseCase;
import com.arka.usecase.processwebhook.ProcessWebhookUseCase;
import com.arka.usecase.retryshipment.RetryShipmentUseCase;
import com.arka.usecase.updateshipmentstatus.UpdateShipmentStatusUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/shipments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ApiRest {

    private final GetShipmentUseCase getShipmentUseCase;
    private final ListShipmentsUseCase listShipmentsUseCase;
    private final UpdateShipmentStatusUseCase updateShipmentStatusUseCase;
    private final RetryShipmentUseCase retryShipmentUseCase;
    private final ProcessWebhookUseCase processWebhookUseCase;

    @GetMapping("/{orderId}")
    public Mono<Shipment> getShipment(@PathVariable UUID orderId) {
        return getShipmentUseCase.execute(orderId);
    }

    @GetMapping
    public Flux<Shipment> listShipments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String carrier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listShipmentsUseCase.execute(status, carrier, page, size);
    }

    @PutMapping("/{orderId}/status")
    public Mono<Shipment> updateStatus(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        return updateShipmentStatusUseCase.execute(orderId, newStatus, null);
    }

    @PostMapping("/retry/{orderId}")
    public Mono<Shipment> retryShipment(@PathVariable UUID orderId) {
        return retryShipmentUseCase.execute(orderId);
    }

    @PostMapping("/webhooks/tracking")
    public Mono<Shipment> webhookTracking(@RequestBody Map<String, String> body) {
        String trackingNumber = body.get("trackingNumber");
        String newStatus = body.get("status");
        String deliveryDateStr = body.get("deliveryDate");
        Instant deliveryDate = deliveryDateStr != null ? Instant.parse(deliveryDateStr) : null;
        return processWebhookUseCase.execute(trackingNumber, newStatus, deliveryDate);
    }
}
