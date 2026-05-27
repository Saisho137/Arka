package com.arka.kafka.consumer;

import com.arka.model.eventstore.EventEnvelope;
import com.arka.usecase.eventconsumption.EventConsumptionUseCase;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventConsumer {

    private final EventConsumptionUseCase eventConsumptionUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {
                    "${kafka.topics.product-events}",
                    "${kafka.topics.inventory-events}",
                    "${kafka.topics.order-events}",
                    "${kafka.topics.cart-events}",
                    "${kafka.topics.payment-events}",
                    "${kafka.topics.shipping-events}",
                    "${kafka.topics.provider-events}"
            },
            groupId = "reporter-service-group"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            EventEnvelope envelope = deserializeEnvelope(record.value());
            eventConsumptionUseCase.consume(envelope);
            ack.acknowledge();
            log.debug("Event consumed: topic={}, type={}, eventId={}",
                    record.topic(), envelope.eventType(), envelope.eventId());
        } catch (JacksonException e) {
            log.error("Failed to deserialize event, skipping: topic={}, offset={}, error={}",
                    record.topic(), record.offset(), e.getMessage());
            ack.acknowledge(); // Skip malformed messages
        } catch (Exception e) {
            log.error("Failed to process event: topic={}, offset={}", record.topic(), record.offset(), e);
            // Don't acknowledge — will be retried
        }
    }

    @SuppressWarnings("unchecked")
    private EventEnvelope deserializeEnvelope(String json) throws JacksonException {
        Map<String, Object> raw = objectMapper.readValue(json, Map.class);

        UUID eventId = UUID.fromString((String) raw.get("eventId"));
        String eventType = (String) raw.get("eventType");
        String source = (String) raw.get("source");
        String correlationIdStr = (String) raw.get("correlationId");
        UUID correlationId = correlationIdStr != null ? UUID.fromString(correlationIdStr) : null;
        Object payload = raw.get("payload");

        String timestampStr = (String) raw.get("timestamp");
        Instant timestamp = timestampStr != null ? Instant.parse(timestampStr) : Instant.now();

        return EventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .timestamp(timestamp)
                .source(source)
                .correlationId(correlationId)
                .payload(payload)
                .build();
    }
}
