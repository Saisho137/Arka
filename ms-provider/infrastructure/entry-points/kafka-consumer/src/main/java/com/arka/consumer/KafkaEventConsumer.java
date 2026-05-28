package com.arka.consumer;

import com.arka.usecase.generatepurchaseorder.GeneratePurchaseOrderUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class KafkaEventConsumer {

    static final int MAX_RETRY_ATTEMPTS = 3;
    static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(500);
    static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(10);

    private final GeneratePurchaseOrderUseCase generatePurchaseOrderUseCase;
    private final KafkaReceiver<String, String> inventoryEventsReceiver;
    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(
            GeneratePurchaseOrderUseCase generatePurchaseOrderUseCase,
            KafkaReceiver<String, String> inventoryEventsReceiver,
            ObjectMapper objectMapper) {
        this.generatePurchaseOrderUseCase = generatePurchaseOrderUseCase;
        this.inventoryEventsReceiver = inventoryEventsReceiver;
        this.objectMapper = objectMapper;
    }

    public void startConsuming() {
        consumeInventoryEvents();
    }

    private void consumeInventoryEvents() {
        inventoryEventsReceiver.receive()
                .flatMap(msg -> handleInventoryEvent(msg.value())
                        .doOnSuccess(v -> msg.receiverOffset().acknowledge())
                        .onErrorResume(ex -> {
                            log.error("Unrecoverable error processing inventory event offset={}: {}",
                                    msg.receiverOffset().offset(), ex.getMessage());
                            msg.receiverOffset().acknowledge();
                            return Mono.empty();
                        }))
                .subscribe(
                        null,
                        ex -> log.error("Inventory events consumer terminated unexpectedly — stream closed", ex)
                );
    }

    Mono<Void> handleInventoryEvent(String rawValue) {
        return Mono.fromCallable(() -> objectMapper.readTree(rawValue))
                .flatMap(envelope -> {
                    String eventType = envelope.path("eventType").asText();
                    return switch (eventType) {
                        case "StockDepleted" -> processStockDepleted(envelope)
                                .retryWhen(retrySpec("StockDepleted"));
                        default -> {
                            log.warn("Unknown eventType '{}' on topic inventory-events — ignoring", eventType);
                            yield Mono.empty();
                        }
                    };
                });
    }

    private Mono<Void> processStockDepleted(JsonNode envelope) {
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        JsonNode payload = envelope.path("payload");

        String sku = payload.path("sku").asText();
        int currentStock = payload.path("currentStock").asInt(0);
        int threshold = payload.path("threshold").asInt(10);

        log.debug("Processing StockDepleted eventId={} sku={} currentStock={} threshold={}",
                eventId, sku, currentStock, threshold);
        return generatePurchaseOrderUseCase.execute(eventId, sku, currentStock, threshold);
    }

    private Retry retrySpec(String eventType) {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                .maxBackoff(RETRY_MAX_BACKOFF)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying {} processing (attempt {}): {}",
                        eventType, signal.totalRetries() + 1, signal.failure().getMessage()));
    }
}
