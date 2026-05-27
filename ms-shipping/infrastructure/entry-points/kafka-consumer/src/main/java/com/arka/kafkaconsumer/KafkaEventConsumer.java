package com.arka.kafkaconsumer;

import com.arka.model.shipment.Carrier;
import com.arka.model.shipment.DeliveryAddress;
import com.arka.usecase.processshipment.ProcessShipmentUseCase;
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

@Slf4j
@Component
public class KafkaEventConsumer {

    static final int MAX_RETRY_ATTEMPTS = 3;
    static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(500);
    static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(10);

    private final ProcessShipmentUseCase processShipmentUseCase;
    private final KafkaReceiver<String, String> orderEventsReceiver;
    private final ObjectMapper objectMapper;

    public KafkaEventConsumer(
            ProcessShipmentUseCase processShipmentUseCase,
            @Qualifier("orderEventsReceiver") KafkaReceiver<String, String> orderEventsReceiver,
            ObjectMapper objectMapper) {
        this.processShipmentUseCase = processShipmentUseCase;
        this.orderEventsReceiver = orderEventsReceiver;
        this.objectMapper = objectMapper;
    }

    public void startConsuming() {
        consumeOrderEvents();
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

    Mono<Void> handleOrderEvent(String rawValue) {
        return Mono.fromCallable(() -> objectMapper.readTree(rawValue))
                .flatMap(envelope -> {
                    String eventType = envelope.path("eventType").asText();
                    return switch (eventType) {
                        case "OrderConfirmed" -> processOrderConfirmed(envelope)
                                .retryWhen(retrySpec("OrderConfirmed"));
                        default -> {
                            log.warn("Unknown eventType '{}' on topic order-events — ignoring", eventType);
                            yield Mono.empty();
                        }
                    };
                });
    }

    private Mono<Void> processOrderConfirmed(JsonNode envelope) {
        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        JsonNode payload = envelope.path("payload");

        UUID orderId = UUID.fromString(payload.path("orderId").asText());
        String preferredCarrier = payload.path("preferredCarrier").asText("DHL");

        JsonNode addressNode = payload.path("deliveryAddress");
        DeliveryAddress address = new DeliveryAddress(
                addressNode.path("street").asText(),
                addressNode.path("city").asText(),
                addressNode.path("state").asText(),
                addressNode.path("postalCode").asText(),
                addressNode.path("country").asText()
        );

        Carrier carrier = Carrier.fromValue(preferredCarrier);

        log.debug("Processing OrderConfirmed eventId={} orderId={}", eventId, orderId);
        return processShipmentUseCase.execute(eventId, orderId, address, carrier);
    }

    private Retry retrySpec(String eventType) {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                .maxBackoff(RETRY_MAX_BACKOFF)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying {} processing (attempt {}): {}",
                        eventType, signal.totalRetries() + 1, signal.failure().getMessage()));
    }
}
