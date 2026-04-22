package com.arka.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts Kafka consumers after the application context is fully initialized.
 * Using ApplicationReadyEvent ensures all beans are ready before consuming.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaConsumerLifecycle {

    private final KafkaEventConsumer kafkaEventConsumer;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Starting Kafka consumer for order-events — ms-payment mock");
        kafkaEventConsumer.startConsuming();
    }
}
