package com.arka.grpc.inventory;

import com.arka.grpc.InventoryServiceGrpc;
import com.arka.grpc.ReserveStockRequest;
import com.arka.grpc.ReserveStockResponse;
import com.arka.model.commons.exception.InventoryServiceUnavailableException;
import com.arka.model.order.ReserveStockResult;
import com.arka.model.order.gateways.InventoryClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class GrpcInventoryClient implements InventoryClient {

    private final InventoryServiceGrpc.InventoryServiceStub inventoryStub;
    private final CircuitBreaker circuitBreaker;

    public GrpcInventoryClient(@GrpcClient("ms-inventory") InventoryServiceGrpc.InventoryServiceStub inventoryStub) {
        this.inventoryStub = inventoryStub;
        this.circuitBreaker = CircuitBreaker.of("grpc-inventory", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .build());
    }

    @Override
    public Mono<ReserveStockResult> reserveStock(String sku, UUID orderId, int quantity) {
        return Mono.<ReserveStockResult>create(sink -> {
            ReserveStockRequest request = ReserveStockRequest.newBuilder()
                    .setSku(sku)
                    .setOrderId(orderId.toString())
                    .setQuantity(quantity)
                    .build();

            inventoryStub.reserveStock(request, new StreamObserver<>() {
                @Override
                public void onNext(ReserveStockResponse response) {
                    UUID reservationId = null;
                    if (!response.getReservationId().isBlank()) {
                        try {
                            reservationId = UUID.fromString(response.getReservationId());
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid reservationId format received: {}", response.getReservationId());
                        }
                    }
                    sink.success(ReserveStockResult.builder()
                            .success(response.getSuccess())
                            .reservationId(reservationId)
                            .availableQuantity(response.getAvailableQuantity())
                            .reason(response.getReason().isBlank() ? null : response.getReason())
                            .build());
                }

                @Override
                public void onError(Throwable t) {
                    log.error("gRPC reserveStock error for sku={}, orderId={}: {}", sku, orderId, t.getMessage());
                    if (t instanceof StatusRuntimeException sre) {
                        Status.Code code = sre.getStatus().getCode();
                        if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                            sink.error(new InventoryServiceUnavailableException(
                                    "Inventory service unavailable: " + sre.getStatus().getDescription()));
                            return;
                        }
                    }
                    sink.error(new InventoryServiceUnavailableException(
                            "Unexpected error communicating with inventory service: " + t.getMessage()));
                }

                @Override
                public void onCompleted() {
                    // handled in onNext
                }
            });
        }).transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
                        ex -> new InventoryServiceUnavailableException(
                                "Inventory service circuit breaker is open — requests not permitted"));
    }
}
