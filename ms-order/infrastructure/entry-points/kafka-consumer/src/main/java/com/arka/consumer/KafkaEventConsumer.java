package com.arka.consumer;

import com.arka.usecase.order.OrderUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

/**
 * Entry point: Kafka consumer for payment-events and shipping-events topics.
 * Uses reactor-kafka KafkaReceiver directly — the correct approach for Spring Boot 4.0.3
 * since ReactiveKafkaConsumerTemplate was removed from spring-kafka 4.0 GA.
 * Deserializes the standard DomainEventEnvelope and routes by eventType.
 * Retries transient errors with exponential backoff (3 attempts).
 * 
 * Phase 1: Infrastructure base ready. Routing logic for PaymentProcessed, PaymentFailed,
 * and ShippingDispatched will be implemented in Phase 2+.
 */
@Slf4j
@Component
public class KafkaEventConsumer {

    static final int MAX_RETRY_ATTEMPTS = 3;
    static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(500);
    static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(10);

    private final OrderUseCase orderUseCase;
    private final KafkaReceiver<String, String> paymentEventsReceiver;
    private final KafkaReceiver<String, String> shippingEventsReceiver;
    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(
            OrderUseCase orderUseCase,
            @Qualifier("paymentEventsReceiver") KafkaReceiver<String, String> paymentEventsReceiver,
            @Qualifier("shippingEventsReceiver") KafkaReceiver<String, String> shippingEventsReceiver,
            ObjectMapper objectMapper) {
        this.orderUseCase = orderUseCase;
        this.paymentEventsReceiver = paymentEventsReceiver;
        this.shippingEventsReceiver = shippingEventsReceiver;
        this.objectMapper = objectMapper;
    }

    public void startConsuming() {
        consumePaymentEvents();
        consumeShippingEvents();
    }

    private void consumePaymentEvents() {
        paymentEventsReceiver.receive()
                .flatMap(msg -> handlePaymentEvent(msg.value())
                        .doOnSuccess(v -> msg.receiverOffset().acknowledge())
                        .onErrorResume(ex -> {
                            log.error("Unrecoverable error processing payment event offset={}: {}",
                                    msg.receiverOffset().offset(), ex.getMessage());
                            msg.receiverOffset().acknowledge();
                            return Mono.empty();
                        }))
                .subscribe(
                        null,
                        ex -> log.error("Payment events consumer terminated unexpectedly — stream closed", ex)
                );
    }

    private void consumeShippingEvents() {
        shippingEventsReceiver.receive()
                .flatMap(msg -> handleShippingEvent(msg.value())
                        .doOnSuccess(v -> msg.receiverOffset().acknowledge())
                        .onErrorResume(ex -> {
                            log.error("Unrecoverable error processing shipping event offset={}: {}",
                                    msg.receiverOffset().offset(), ex.getMessage());
                            msg.receiverOffset().acknowledge();
                            return Mono.empty();
                        }))
                .subscribe(
                        null,
                        ex -> log.error("Shipping events consumer terminated unexpectedly — stream closed", ex)
                );
    }

    // Package-private for testing
    Mono<Void> handlePaymentEvent(String rawValue) {
        return Mono.fromCallable(() -> objectMapper.readTree(rawValue))
                .flatMap(envelope -> {
                    String eventType = envelope.path("eventType").asText();
                    UUID eventId = UUID.fromString(envelope.path("eventId").asText());
                    
                    return switch (eventType) {
                        case "PaymentProcessed" -> processPaymentProcessed(eventId, envelope)
                                .retryWhen(retrySpec("PaymentProcessed"));
                        case "PaymentFailed" -> processPaymentFailed(eventId, envelope)
                                .retryWhen(retrySpec("PaymentFailed"));
                        default -> {
                            log.warn("Unknown eventType '{}' on topic payment-events — ignoring", eventType);
                            yield Mono.empty();
                        }
                    };
                });
    }

    // Package-private for testing
    Mono<Void> handleShippingEvent(String rawValue) {
        return Mono.fromCallable(() -> objectMapper.readTree(rawValue))
                .flatMap(envelope -> {
                    String eventType = envelope.path("eventType").asText();
                    UUID eventId = UUID.fromString(envelope.path("eventId").asText());
                    
                    return switch (eventType) {
                        case "ShippingDispatched" -> processShippingDispatched(eventId, envelope)
                                .retryWhen(retrySpec("ShippingDispatched"));
                        default -> {
                            log.warn("Unknown eventType '{}' on topic shipping-events — ignoring", eventType);
                            yield Mono.empty();
                        }
                    };
                });
    }

    private Mono<Void> processPaymentProcessed(UUID eventId, JsonNode envelope) {
        log.debug("Processing PaymentProcessed eventId={}", eventId);
        String orderIdStr = envelope.path("payload").path("orderId").asText("");
        if (orderIdStr.isBlank()) {
            log.error("PaymentProcessed eventId={} missing orderId in payload — skipping", eventId);
            return Mono.empty();
        }
        UUID orderId = UUID.fromString(orderIdStr);
        return orderUseCase.processPaymentProcessed(eventId, orderId);
    }

    private Mono<Void> processPaymentFailed(UUID eventId, JsonNode envelope) {
        log.debug("Processing PaymentFailed eventId={}", eventId);
        JsonNode payload = envelope.path("payload");
        String orderIdStr = payload.path("orderId").asText("");
        if (orderIdStr.isBlank()) {
            log.error("PaymentFailed eventId={} missing orderId in payload — skipping", eventId);
            return Mono.empty();
        }
        UUID orderId = UUID.fromString(orderIdStr);
        String reason = payload.path("reason").asText(null);
        return orderUseCase.processPaymentFailed(eventId, orderId, reason);
    }

    private Mono<Void> processShippingDispatched(UUID eventId, JsonNode envelope) {
        log.debug("Processing ShippingDispatched eventId={} — Phase 2+ (Task 14)", eventId);
        // Task 14: extract orderId and delegate to orderUseCase.processShippingDispatched()
        return orderUseCase.processExternalEvent(eventId);
    }

    private Retry retrySpec(String eventType) {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                .maxBackoff(RETRY_MAX_BACKOFF)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying {} processing (attempt {}): {}",
                        eventType, signal.totalRetries() + 1, signal.failure().getMessage()));
    }
}
