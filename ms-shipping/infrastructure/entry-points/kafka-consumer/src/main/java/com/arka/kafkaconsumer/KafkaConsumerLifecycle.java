package com.arka.kafkaconsumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaConsumerLifecycle {

    private final KafkaEventConsumer kafkaEventConsumer;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Starting Kafka consumer for order-events");
        kafkaEventConsumer.startConsuming();
    }
}
