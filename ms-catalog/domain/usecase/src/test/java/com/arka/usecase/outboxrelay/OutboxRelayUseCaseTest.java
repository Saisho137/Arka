package com.arka.usecase.outboxrelay;

import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayUseCaseTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxRelayUseCase useCase;

    @Test
    @DisplayName("Should fetch pending events")
    void shouldFetchPending() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(EventType.PRODUCT_CREATED)
                .payload("{}")
                .partitionKey("PROD-001")
                .build();
        when(outboxEventRepository.findPending(100)).thenReturn(Flux.just(event));

        StepVerifier.create(useCase.fetchPendingEvents())
                .expectNextMatches(e -> e.eventType() == EventType.PRODUCT_CREATED)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should mark event as published")
    void shouldMarkAsPublished() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(EventType.PRODUCT_UPDATED)
                .payload("{}")
                .partitionKey("PROD-002")
                .build();
        when(outboxEventRepository.markAsPublished(event.id())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.markAsPublished(event))
                .verifyComplete();

        verify(outboxEventRepository).markAsPublished(event.id());
    }
}
