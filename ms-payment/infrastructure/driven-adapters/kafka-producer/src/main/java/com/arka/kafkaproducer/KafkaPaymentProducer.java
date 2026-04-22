package com.arka.kafkaproducer;

import com.arka.model.payment.event.DomainEventEnvelope;
import com.arka.model.payment.gateways.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentProducer implements PaymentEventPublisher {

    @Value("${kafka.producer.topics.payment-events:payment-events}")
    private String paymentEventsTopic;

    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> publishPaymentEvent(String orderId, DomainEventEnvelope envelope) {
        return Mono.fromCallable(() -> serializeEnvelope(envelope))
                .flatMap(json -> send(orderId, json))
                .doOnSuccess(v -> log.info("Published {} event for orderId={}", envelope.eventType(), orderId));
    }

    private Mono<Void> send(String key, String value) {
        var producerRecord = new ProducerRecord<>(paymentEventsTopic, null, null, key, value);
        var senderRecord = SenderRecord.create(producerRecord, key);
        return kafkaSender.send(Mono.just(senderRecord))
                .next()
                .then();
    }

    private String serializeEnvelope(DomainEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize DomainEventEnvelope for eventType=" + envelope.eventType(), e);
        }
    }
}
