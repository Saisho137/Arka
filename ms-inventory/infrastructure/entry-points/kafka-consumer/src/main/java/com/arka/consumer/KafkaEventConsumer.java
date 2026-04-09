package com.arka.consumer;

import com.arka.usecase.stock.StockUseCase;
import com.arka.usecase.stockreservation.StockReservationUseCase;
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
 * Entry point: Kafka consumer for product-events and order-events topics.
 * Uses reactor-kafka KafkaReceiver directly — the correct approach for Spring Boot 4.0.3
 * since ReactiveKafkaConsumerTemplate was removed from spring-kafka 4.0 GA
 * (Reactor Kafka was discontinued by the Spring team in May 2025).
 * Deserializes the standard DomainEventEnvelope and routes by eventType.
 * Retries transient errors with exponential backoff (3 attempts).
 */
@Slf4j
@Component
public class KafkaEventConsumer {

    static final int MAX_RETRY_ATTEMPTS = 3;
    static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(500);
    static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(10);

    private final StockUseCase stockUseCase;
    private final StockReservationUseCase stockReservationUseCase;
    private final KafkaReceiver<String, String> productEventsReceiver;
    private final KafkaReceiver<String, String> orderEventsReceiver;
    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(
            StockUseCase stockUseCase,
            StockReservationUseCase stockReservationUseCase,
            @Qualifier("productEventsReceiver") KafkaReceiver<String, String> productEventsReceiver,
            @Qualifier("orderEventsReceiver") KafkaReceiver<String, String> orderEventsReceiver,
            ObjectMapper objectMapper) {
        this.stockUseCase = stockUseCase;
        this.stockReservationUseCase = stockReservationUseCase;
        this.productEventsReceiver = productEventsReceiver;
        this.orderEventsReceiver = orderEventsReceiver;
        this.objectMapper = objectMapper;
    }

    public void startConsuming() {
        consumeProductEvents();
        consumeOrderEvents();
    }

    private void consumeProductEvents() {
        productEventsReceiver.receive()
                .flatMap(msg -> handleProductEvent(msg.value())
                        .doOnSuccess(v -> msg.receiverOffset().acknowledge())
                        .onErrorResume(ex -> {
                            log.error("Unrecoverable error processing product event offset={}: {}",
                                    msg.receiverOffset().offset(), ex.getMessage());
                            msg.receiverOffset().acknowledge();
                            return Mono.empty();
                        }))
                .subscribe(
                        null,
                        ex -> log.error("Product events consumer terminated unexpectedly — stream closed", ex)
                );
    }

    private void consumeOrderEvents() {
        orderEventsReceiver.receive()
                .flatMap(msg -> handleOrderEvent(msg.value())
                        .doOnSuccess(v -> msg.receiverOffset().acknowledge())
                        .onErrorResume(ex -> {
                            log.error("Unrecoverable error processing order event offset={}: {}",
                                    msg.receiverOffset().offset(), ex.getMessage());
                            msg.receiverOffset().acknowledge();
                            return Mono.empty();
                        }))
                .subscribe(
                        null,
                        ex -> log.error("Order events consumer terminated unexpectedly — stream closed", ex)
                );
    }

    // Package-private for testing
    Mono<Void> handleProductEvent(String rawValue) {
        return Mono.fromCallable(() -> objectMapper.readTree(rawValue))
                .flatMap(envelope -> {
                    String eventType = envelope.path("eventType").asText();
                    return switch (eventType) {
                        case "ProductCreated" -> processProductCreated(envelope)
                                .retryWhen(retrySpec("ProductCreated"));
                        default -> {
                            log.warn("Unknown eventType '{}' on topic product-events — ignoring", eventType);
                            yield Mono.empty();
                        }
                    };
                });
    }

    // Package-private for testing
    Mono<Void> handleOrderEvent(String rawValue) {
        return Mono.fromCallable(() -> objectMapper.readTree(rawValue))
                .flatMap(envelope -> {
                    String eventType = envelope.path("eventType").asText();
                    return switch (eventType) {
                        case "OrderCancelled" -> processOrderCancelled(envelope)
                                .retryWhen(retrySpec("OrderCancelled"));
                        default -> {
                            log.warn("Unknown eventType '{}' on topic order-events — ignoring", eventType);
                            yield Mono.empty();
                        }
                    };
                });
    }

    private Mono<Void> processProductCreated(JsonNode envelope) {
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        JsonNode payload = envelope.path("payload");

        String sku = payload.path("sku").asText();
        UUID productId = UUID.fromString(payload.path("productId").asText());
        int initialStock = payload.path("initialStock").asInt(0);
        int depletionThreshold = payload.path("depletionThreshold").asInt(10);

        log.debug("Processing ProductCreated eventId={} sku={}", eventId, sku);
        return stockUseCase.processProductCreated(eventId, sku, productId, initialStock, depletionThreshold);
    }

    private Mono<Void> processOrderCancelled(JsonNode envelope) {
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        JsonNode payload = envelope.path("payload");

        UUID orderId = UUID.fromString(payload.path("orderId").asText());
        String sku = payload.path("sku").asText();

        log.debug("Processing OrderCancelled eventId={} orderId={}", eventId, orderId);
        return stockReservationUseCase.processOrderCancelled(eventId, orderId, sku);
    }

    private Retry retrySpec(String eventType) {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                .maxBackoff(RETRY_MAX_BACKOFF)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying {} processing (attempt {}): {}",
                        eventType, signal.totalRetries() + 1, signal.failure().getMessage()));
    }
}
