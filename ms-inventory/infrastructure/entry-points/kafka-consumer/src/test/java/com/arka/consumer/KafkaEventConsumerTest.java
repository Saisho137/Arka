package com.arka.consumer;

import com.arka.usecase.stock.StockUseCase;
import com.arka.usecase.stockreservation.StockReservationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventConsumerTest {

    @Mock
    private StockUseCase stockUseCase;
    @Mock
    private StockReservationUseCase stockReservationUseCase;
    @Mock
    private KafkaReceiver<String, String> productEventsReceiver;
    @Mock
    private KafkaReceiver<String, String> orderEventsReceiver;

    private KafkaEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new KafkaEventConsumer(
                stockUseCase, stockReservationUseCase,
                productEventsReceiver, orderEventsReceiver,
                objectMapper);
    }

    @Test
    void handleProductEvent_ProductCreated_delegatesToStockUseCase() {
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String sku = "SKU-001";

        String rawValue = """
                {
                  "eventId": "%s",
                  "eventType": "ProductCreated",
                  "payload": {
                    "sku": "%s",
                    "productId": "%s",
                    "initialStock": 100,
                    "depletionThreshold": 10
                  }
                }
                """.formatted(eventId, sku, productId);

        when(stockUseCase.processProductCreated(eventId, sku, productId, 100, 10))
                .thenReturn(Mono.empty());

        StepVerifier.create(consumer.handleProductEvent(rawValue))
                .verifyComplete();

        verify(stockUseCase).processProductCreated(eventId, sku, productId, 100, 10);
    }

    @Test
    void handleOrderEvent_OrderCancelled_delegatesToStockReservationUseCase() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        String rawValue = """
                {
                  "eventId": "%s",
                  "eventType": "OrderCancelled",
                  "payload": {
                    "orderId": "%s",
                    "customerId": "abc",
                    "reason": "customer request"
                  }
                }
                """.formatted(eventId, orderId);

        when(stockReservationUseCase.processOrderCancelled(eventId, orderId))
                .thenReturn(Mono.empty());

        StepVerifier.create(consumer.handleOrderEvent(rawValue))
                .verifyComplete();

        verify(stockReservationUseCase).processOrderCancelled(eventId, orderId);
    }

    @Test
    void handleOrderEvent_OrderConfirmed_delegatesToStockReservationUseCase() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        String rawValue = """
                {
                  "eventId": "%s",
                  "eventType": "OrderConfirmed",
                  "payload": {
                    "orderId": "%s"
                  }
                }
                """.formatted(eventId, orderId);

        when(stockReservationUseCase.processOrderConfirmed(eventId, orderId))
                .thenReturn(Mono.empty());

        StepVerifier.create(consumer.handleOrderEvent(rawValue))
                .verifyComplete();

        verify(stockReservationUseCase).processOrderConfirmed(eventId, orderId);
    }

    @Test
    void handleProductEvent_unknownEventType_logsWarnAndCompletes() {
        String rawValue = """
                {
                  "eventId": "%s",
                  "eventType": "UnknownEvent",
                  "payload": {}
                }
                """.formatted(UUID.randomUUID());

        StepVerifier.create(consumer.handleProductEvent(rawValue))
                .verifyComplete();

        verifyNoInteractions(stockUseCase);
    }

    @Test
    void handleOrderEvent_unknownEventType_logsWarnAndCompletes() {
        String rawValue = """
                {
                  "eventId": "%s",
                  "eventType": "UnknownEvent",
                  "payload": {}
                }
                """.formatted(UUID.randomUUID());

        StepVerifier.create(consumer.handleOrderEvent(rawValue))
                .verifyComplete();

        verifyNoInteractions(stockReservationUseCase);
    }

    @Test
    void handleProductEvent_idempotent_useCaseReturnsEmpty() {
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String sku = "SKU-004";

        String rawValue = """
                {
                  "eventId": "%s",
                  "eventType": "ProductCreated",
                  "payload": {
                    "sku": "%s",
                    "productId": "%s",
                    "initialStock": 20,
                    "depletionThreshold": 5
                  }
                }
                """.formatted(eventId, sku, productId);

        when(stockUseCase.processProductCreated(eventId, sku, productId, 20, 5))
                .thenReturn(Mono.empty());

        StepVerifier.create(consumer.handleProductEvent(rawValue))
                .verifyComplete();

        verify(stockUseCase, times(1)).processProductCreated(eventId, sku, productId, 20, 5);
    }
}
