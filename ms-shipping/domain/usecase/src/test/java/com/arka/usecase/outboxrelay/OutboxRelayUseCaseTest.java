package com.arka.usecase.outboxrelay;

import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.OutboxStatus;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayUseCaseTest {

    @Mock private OutboxEventRepository outboxEventRepository;

    private OutboxRelayUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OutboxRelayUseCase(outboxEventRepository);
    }

    @Test
    @DisplayName("Should fetch pending events with batch size 100")
    void shouldFetchPendingEvents() {
        OutboxEvent event = buildOutboxEvent();
        when(outboxEventRepository.findPending(100)).thenReturn(Flux.just(event));

        StepVerifier.create(useCase.fetchPendingEvents())
                .expectNextMatches(e -> e.isPending() && e.partitionKey().equals("order-123"))
                .verifyComplete();

        verify(outboxEventRepository).findPending(100);
    }

    @Test
    @DisplayName("Should return empty flux when no pending events")
    void shouldReturnEmptyWhenNoPending() {
        when(outboxEventRepository.findPending(100)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.fetchPendingEvents())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should mark event as published")
    void shouldMarkAsPublished() {
        OutboxEvent event = buildOutboxEvent();
        when(outboxEventRepository.markAsPublished(event.id())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.markAsPublished(event))
                .verifyComplete();

        verify(outboxEventRepository).markAsPublished(event.id());
    }

    private OutboxEvent buildOutboxEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType(EventType.SHIPPING_DISPATCHED)
                .payload("{\"orderId\":\"abc-123\"}")
                .partitionKey("order-123")
                .status(OutboxStatus.PENDING)
                .build();
    }
}
