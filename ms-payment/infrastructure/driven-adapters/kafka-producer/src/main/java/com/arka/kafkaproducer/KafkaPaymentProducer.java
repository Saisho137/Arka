package com.arka.kafkaproducer;

import com.arka.model.payment.event.DomainEventEnvelope;
import com.arka.model.payment.event.OutboxEvent;
import com.arka.model.payment.gateways.PaymentEventPublisher;
import com.arka.usecase.outboxrelay.OutboxRelayUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentProducer implements PaymentEventPublisher {

    private static final String TOPIC = "payment-events";

    private final OutboxRelayUseCase outboxRelayUseCase;
    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${scheduler.outbox-relay.interval:5000}")
    public void relay() {
        outboxRelayUseCase.fetchPendingEvents()
                .flatMap(this::publishAndMark)
                .subscribe();
    }

    private Mono<Void> publishAndMark(OutboxEvent event) {
        return Mono.fromCallable(() -> buildEnvelopeJson(event))
                .flatMap(json -> send(event.partitionKey(), json, event))
                .then(Mono.defer(() -> outboxRelayUseCase.markAsPublished(event)))
                .doOnSuccess(v -> log.info("Published outbox event {} [{}] to Kafka",
                        event.id(), event.eventType()))
                .onErrorResume(ex -> {
                    log.warn("Failed to publish outbox event {} [{}]: {}",
                            event.id(), event.eventType(), ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> send(String key, String value, OutboxEvent event) {
        var producerRecord = new ProducerRecord<>(TOPIC, null, null, key, value);
        var senderRecord = SenderRecord.create(producerRecord, event.id());
        return kafkaSender.send(Mono.just(senderRecord))
                .next()
                .then();
    }

    private String buildEnvelopeJson(OutboxEvent event) {
        Object parsedPayload;
        try {
            parsedPayload = objectMapper.readTree(event.payload());
        } catch (JacksonException e) {
            parsedPayload = event.payload();
        }

        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(event.id().toString())
                .eventType(event.eventType().value())
                .timestamp(event.createdAt() != null ? event.createdAt() : Instant.now())
                .source(DomainEventEnvelope.MS_SOURCE)
                .correlationId(event.partitionKey())
                .payload(parsedPayload)
                .build();

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize DomainEventEnvelope for event " + event.id(), e);
        }
    }

    @Override
    public Mono<Void> publishPaymentEvent(String orderId, DomainEventEnvelope envelope) {
        // Legacy method - kept for backward compatibility
        // New flow uses Outbox Relay pattern
        return Mono.fromCallable(() -> serializeEnvelope(envelope))
                .flatMap(json -> {
                    var producerRecord = new ProducerRecord<>(TOPIC, null, null, orderId, json);
                    var senderRecord = SenderRecord.create(producerRecord, orderId);
                    return kafkaSender.send(Mono.just(senderRecord)).next().then();
                })
                .doOnSuccess(v -> log.info("Published {} event for orderId={}", envelope.eventType(), orderId));
    }

    private String serializeEnvelope(DomainEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize DomainEventEnvelope for eventType=" + envelope.eventType(), e);
        }
    }
}
