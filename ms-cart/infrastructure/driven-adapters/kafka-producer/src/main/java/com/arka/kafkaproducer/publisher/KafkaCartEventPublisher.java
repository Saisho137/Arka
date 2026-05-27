package com.arka.kafkaproducer.publisher;

import com.arka.model.cart.gateways.CartEventPublisher;
import com.arka.model.event.CartAbandonedEvent;
import com.arka.model.event.DomainEventEnvelope;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCartEventPublisher implements CartEventPublisher {

    private static final String TOPIC = "cart-events";
    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> publishCartAbandoned(CartAbandonedEvent event) {
        var envelope = DomainEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CartAbandoned")
                .timestamp(Instant.now())
                .source(DomainEventEnvelope.MS_SOURCE)
                .correlationId(UUID.randomUUID().toString())
                .payload(event)
                .build();

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            return Mono.error(e);
        }

        var record = new ProducerRecord<>(TOPIC, event.cartId().toString(), json);
        var senderRecord = SenderRecord.create(record, event.cartId().toString());

        return kafkaSender.send(Mono.just(senderRecord))
                .next()
                .doOnNext(result -> log.info("Published CartAbandoned event for cart {}", event.cartId()))
                .doOnError(e -> log.error("Failed to publish CartAbandoned event for cart {}: {}", event.cartId(), e.getMessage()))
                .then();
    }
}
