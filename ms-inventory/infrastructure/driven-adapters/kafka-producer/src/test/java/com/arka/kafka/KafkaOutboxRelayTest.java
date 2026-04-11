package com.arka.kafka;

import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.OutboxStatus;
import com.arka.usecase.outboxrelay.OutboxRelayUseCase;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxRelayTest {

    @Mock
    private OutboxRelayUseCase outboxRelayUseCase;

    @Mock
    private KafkaSender<String, String> kafkaSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KafkaOutboxRelay kafkaOutboxRelay;

    @BeforeEach
    void setUp() {
        kafkaOutboxRelay = new KafkaOutboxRelay(outboxRelayUseCase, kafkaSender, objectMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void relay_shouldPublishPendingEventsAndMarkAsPublished() {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(EventType.STOCK_RESERVED)
                .payload("{\"sku\":\"SKU-001\",\"orderId\":\"order-1\",\"quantity\":5}")
                .partitionKey("SKU-001")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(outboxRelayUseCase.fetchPendingEvents()).thenReturn(Flux.just(event));

        SenderResult<UUID> senderResult = mock(SenderResult.class);
        when(kafkaSender.send(any(Mono.class))).thenReturn(Flux.just(senderResult));
        when(outboxRelayUseCase.markAsPublished(event)).thenReturn(Mono.empty());

        kafkaOutboxRelay.relay();

        verify(outboxRelayUseCase).fetchPendingEvents();
        verify(kafkaSender).send(any(Mono.class));
        verify(outboxRelayUseCase).markAsPublished(event);
    }

    @Test
    void relay_shouldHandleEmptyPendingEvents() {
        when(outboxRelayUseCase.fetchPendingEvents()).thenReturn(Flux.empty());

        kafkaOutboxRelay.relay();

        verify(outboxRelayUseCase).fetchPendingEvents();
        verifyNoInteractions(kafkaSender);
    }

    @Test
    @SuppressWarnings("unchecked")
    void relay_shouldKeepEventPendingOnKafkaFailure() {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(EventType.STOCK_UPDATED)
                .payload("{\"sku\":\"SKU-002\"}")
                .partitionKey("SKU-002")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(outboxRelayUseCase.fetchPendingEvents()).thenReturn(Flux.just(event));
        when(kafkaSender.send(any(Mono.class)))
                .thenReturn(Flux.error(new RuntimeException("Kafka unavailable")));

        kafkaOutboxRelay.relay();

        verify(outboxRelayUseCase).fetchPendingEvents();
        verify(outboxRelayUseCase, never()).markAsPublished(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void relay_shouldSendToKafkaWithSkuAsKey() {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(EventType.STOCK_RELEASED)
                .payload("{\"sku\":\"SKU-003\"}")
                .partitionKey("SKU-003")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(outboxRelayUseCase.fetchPendingEvents()).thenReturn(Flux.just(event));

        SenderResult<UUID> senderResult = mock(SenderResult.class);
        when(kafkaSender.send(any(Mono.class))).thenReturn(Flux.just(senderResult));
        when(outboxRelayUseCase.markAsPublished(event)).thenReturn(Mono.empty());

        kafkaOutboxRelay.relay();

        verify(kafkaSender).send(any(Mono.class));
    }

    @Test
    void toCamelCase_shouldConvertEventTypeCorrectly() {
        assertThat(KafkaOutboxRelay.toCamelCase(EventType.STOCK_RESERVED)).isEqualTo("StockReserved");
        assertThat(KafkaOutboxRelay.toCamelCase(EventType.STOCK_RESERVE_FAILED)).isEqualTo("StockReserveFailed");
        assertThat(KafkaOutboxRelay.toCamelCase(EventType.STOCK_RELEASED)).isEqualTo("StockReleased");
        assertThat(KafkaOutboxRelay.toCamelCase(EventType.STOCK_UPDATED)).isEqualTo("StockUpdated");
        assertThat(KafkaOutboxRelay.toCamelCase(EventType.STOCK_DEPLETED)).isEqualTo("StockDepleted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void relay_shouldProcessMultipleEventsIndependently() {
        OutboxEvent event1 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(EventType.STOCK_RESERVED)
                .payload("{\"sku\":\"SKU-A\"}")
                .partitionKey("SKU-A")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(EventType.STOCK_UPDATED)
                .payload("{\"sku\":\"SKU-B\"}")
                .partitionKey("SKU-B")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(outboxRelayUseCase.fetchPendingEvents()).thenReturn(Flux.just(event1, event2));

        SenderResult<UUID> senderResult = mock(SenderResult.class);
        when(kafkaSender.send(any(Mono.class))).thenReturn(Flux.just(senderResult));
        when(outboxRelayUseCase.markAsPublished(any())).thenReturn(Mono.empty());

        kafkaOutboxRelay.relay();

        verify(outboxRelayUseCase).fetchPendingEvents();
        verify(outboxRelayUseCase, times(2)).markAsPublished(any());
    }
}
