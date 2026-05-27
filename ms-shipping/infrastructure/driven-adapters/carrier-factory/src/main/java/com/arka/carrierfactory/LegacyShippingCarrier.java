package com.arka.carrierfactory;

import com.arka.model.shipment.Carrier;
import com.arka.model.shipment.DeliveryAddress;
import com.arka.model.shipment.ShippingResult;
import com.arka.model.shipment.gateways.ShippingCarrier;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.core.IntervalFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
public class LegacyShippingCarrier implements ShippingCarrier {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;

    public LegacyShippingCarrier() {
        this.circuitBreaker = CircuitBreaker.of("legacy-carrier", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
        this.retry = Retry.of("legacy-carrier", RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(2), 2))
                .build());
        this.bulkhead = Bulkhead.of("legacy-carrier", BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .build());
    }

    @Override
    public Mono<ShippingResult> generateLabel(UUID orderId, DeliveryAddress address) {
        return Mono.fromCallable(() -> simulateLegacyCall(orderId, address))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(30))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(BulkheadOperator.of(bulkhead))
                .onErrorResume(t -> {
                    log.error("Legacy carrier fallback for orderId={}: {}", orderId, t.getMessage());
                    return Mono.just(ShippingResult.builder()
                            .success(false)
                            .reason("Legacy carrier unavailable: " + t.getMessage())
                            .build());
                });
    }

    @Override
    public Carrier supportedCarrier() {
        return Carrier.LEGACY;
    }

    private ShippingResult simulateLegacyCall(UUID orderId, DeliveryAddress address) {
        log.info("Legacy SDK: Generating label for orderId={}", orderId);
        String trackingNumber = "LEG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        byte[] labelPdf = ("Legacy Label for " + orderId).getBytes();
        return ShippingResult.builder()
                .success(true)
                .trackingNumber(trackingNumber)
                .labelPdf(labelPdf)
                .estimatedDeliveryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }
}
