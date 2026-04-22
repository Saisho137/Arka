package com.arka.consumer;

import com.arka.model.notification.DomainEventEnvelope;
import com.arka.usecase.notification.ProcessNotificationUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.util.retry.Retry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
public class KafkaEventConsumer {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(500);
    private static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(10);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ProcessNotificationUseCase processNotificationUseCase;
    private final KafkaReceiver<String, String> orderEventsReceiver;
    private final KafkaReceiver<String, String> inventoryEventsReceiver;
    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(
            ProcessNotificationUseCase processNotificationUseCase,
            @Qualifier("orderEventsReceiver") KafkaReceiver<String, String> orderEventsReceiver,
            @Qualifier("inventoryEventsReceiver") KafkaReceiver<String, String> inventoryEventsReceiver,
            ObjectMapper objectMapper) {
        this.processNotificationUseCase = processNotificationUseCase;
        this.orderEventsReceiver = orderEventsReceiver;
        this.inventoryEventsReceiver = inventoryEventsReceiver;
        this.objectMapper = objectMapper;
    }

    public void startConsuming() {
        consumeOrderEvents();
        consumeInventoryEvents();
    }

    private void consumeOrderEvents() {
        orderEventsReceiver.receive()
                .flatMap(msg -> handleEvent(msg.value(), "order-events")
                        .doOnSuccess(v -> msg.receiverOffset().acknowledge())
                        .onErrorResume(ex -> {
                            log.error("Unrecoverable error on order-events offset={}: {}",
                                    msg.receiverOffset().offset(), ex.getMessage());
                            msg.receiverOffset().acknowledge();
                            return Mono.empty();
                        }))
                .subscribe(null,
                        ex -> log.error("order-events consumer terminated unexpectedly", ex));
    }

    private void consumeInventoryEvents() {
        inventoryEventsReceiver.receive()
                .flatMap(msg -> handleEvent(msg.value(), "inventory-events")
                        .doOnSuccess(v -> msg.receiverOffset().acknowledge())
                        .onErrorResume(ex -> {
                            log.error("Unrecoverable error on inventory-events offset={}: {}",
                                    msg.receiverOffset().offset(), ex.getMessage());
                            msg.receiverOffset().acknowledge();
                            return Mono.empty();
                        }))
                .subscribe(null,
                        ex -> log.error("inventory-events consumer terminated unexpectedly", ex));
    }

    Mono<Void> handleEvent(String rawValue, String topic) {
        return Mono.fromCallable(() -> objectMapper.readTree(rawValue))
                .flatMap(envelope -> {
                    String eventType = envelope.path("eventType").asText();

                    if (!isRelevantEvent(eventType)) {
                        log.debug("Ignoring eventType='{}' on topic={}", eventType, topic);
                        return Mono.empty();
                    }

                    DomainEventEnvelope event = parseEnvelope(envelope);
                    if (event == null) {
                        log.warn("Could not parse envelope on topic={} eventType={}", topic, eventType);
                        return Mono.empty();
                    }

                    log.debug("Processing eventType={} eventId={}", event.eventType(), event.eventId());

                    return processNotificationUseCase.process(event)
                            .doOnSuccess(v -> log.info("Notification processed: eventId={} eventType={}",
                                    event.eventId(), event.eventType()))
                            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                                    .maxBackoff(RETRY_MAX_BACKOFF)
                                    .doBeforeRetry(rs -> log.warn("Retrying eventType={} attempt={}",
                                            eventType, rs.totalRetries() + 1)))
                            .onErrorResume(ex -> {
                                log.error("Failed to process notification eventId={} eventType={}: {}",
                                        event.eventId(), event.eventType(), ex.getMessage());
                                return Mono.empty();
                            });
                });
    }

    private DomainEventEnvelope parseEnvelope(JsonNode envelope) {
        try {
            String eventId = envelope.path("eventId").asText();
            String eventType = envelope.path("eventType").asText();
            String source = envelope.path("source").asText("");
            String correlationId = envelope.path("correlationId").asText("");

            Instant timestamp;
            try {
                timestamp = Instant.parse(envelope.path("timestamp").asText());
            } catch (Exception e) {
                timestamp = Instant.now();
            }

            JsonNode payloadNode = envelope.path("payload");
            Map<String, Object> payload = objectMapper.convertValue(payloadNode, MAP_TYPE);

            return DomainEventEnvelope.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .timestamp(timestamp)
                    .source(source)
                    .correlationId(correlationId)
                    .payload(payload)
                    .build();
        } catch (Exception ex) {
            log.error("Failed to parse Kafka envelope: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isRelevantEvent(String eventType) {
        return switch (eventType) {
            case "OrderConfirmed", "OrderCancelled", "OrderStatusChanged", "StockDepleted" -> true;
            default -> false;
        };
    }
}

