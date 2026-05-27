package com.arka.kafka.producer;

import com.arka.model.eventstore.DomainEventEnvelope;
import com.arka.model.eventstore.gateways.EventPublisherGateway;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements EventPublisherGateway {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.reporter-events}")
    private String reporterEventsTopic;

    @Override
    public void publish(DomainEventEnvelope envelope, String partitionKey) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(reporterEventsTopic, partitionKey, json);
            log.info("Event published: topic={}, type={}, key={}", reporterEventsTopic, envelope.eventType(), partitionKey);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize event envelope", e);
        }
    }
}
