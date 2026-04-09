package com.arka.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private final String bootstrapServers;
    private final String groupId;

    public KafkaConsumerConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:ms-inventory}") String groupId) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
    }

    @Bean
    public KafkaReceiver<String, String> productEventsReceiver() {
        return KafkaReceiver.create(receiverOptions("product-events", groupId + "-product"));
    }

    @Bean
    public KafkaReceiver<String, String> orderEventsReceiver() {
        return KafkaReceiver.create(receiverOptions("order-events", groupId + "-order"));
    }

    private ReceiverOptions<String, String> receiverOptions(String topic, String consumerGroupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return ReceiverOptions.<String, String>create(props)
                .subscription(List.of(topic));
    }
}
