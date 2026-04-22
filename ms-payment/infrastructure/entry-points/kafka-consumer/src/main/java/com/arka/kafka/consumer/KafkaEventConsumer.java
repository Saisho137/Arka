package com.arka.kafka.consumer;

import com.arka.usecase.processpayment.ProcessPaymentUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Entry point: Kafka consumer for order-events topic.
 * Uses reactor-kafka KafkaReceiver directly — the correct approach for Spring Boot 4.0.3
 * since ReactiveKafkaConsumerTemplate was removed from spring-kafka 4.0 GA.
 * Routes by eventType and ignores unknown events.
 */
@Slf4j
@Component
public class KafkaEventConsumer {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final KafkaReceiver<String, String> orderEventsReceiver;
    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(
            ProcessPaymentUseCase processPaymentUseCase,
            @Qualifier("orderEventsReceiver") KafkaReceiver<String, String> orderEventsReceiver,
            ObjectMapper objectMapper) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.orderEventsReceiver = orderEventsReceiver;
        this.objectMapper = objectMapper;
    }

    public void startConsuming() {
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
    Mono<Void> handleOrderEvent(String rawValue) {
        return Mono.fromCallable(() -> objectMapper.readTree(rawValue))
                .flatMap(envelope -> {
                    String eventType = envelope.path("eventType").asText();
                    return switch (eventType) {
                        case "OrderCreated" -> processOrderCreated(envelope);
                        default -> {
                            log.warn("Unknown eventType '{}' on topic order-events — ignoring", eventType);
                            yield Mono.empty();
                        }
                    };
                });
    }

    private Mono<Void> processOrderCreated(JsonNode envelope) {
        JsonNode payload = envelope.path("payload");
        String orderIdStr = payload.path("orderId").asText();
        String eventId = envelope.path("eventId").asText();
        log.debug("Received OrderCreated event for orderId={}", orderIdStr);
        UUID orderId = UUID.fromString(orderIdStr);
        UUID correlationId = UUID.fromString(eventId);
        return processPaymentUseCase.process(orderId, correlationId);
    }
}

