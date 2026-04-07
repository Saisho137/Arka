package com.arka.usecase.stockreservation;

import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.stock.Stock;
import com.arka.model.stock.gateways.StockRepository;
import com.arka.model.stockmovement.StockMovement;
import com.arka.model.stockmovement.gateways.StockMovementRepository;
import com.arka.model.stockreservation.ReservationStatus;
import com.arka.model.stockreservation.StockReservation;
import com.arka.model.stockreservation.gateways.StockReservationRepository;
import com.arka.usecase.stock.JsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationUseCaseTest {

    @Mock private StockReservationRepository stockReservationRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private JsonSerializer jsonSerializer;
    @Mock private ReservationExpirationProcessor reservationExpirationProcessor;

    @InjectMocks
    private StockReservationUseCase useCase;

    private static final String SKU = "SKU-001";
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(jsonSerializer.serialize(any())).thenReturn("{}");
    }

    private Stock buildStock(int quantity, int reserved) {
        return Stock.builder()
                .id(UUID.randomUUID()).sku(SKU).productId(PRODUCT_ID)
                .quantity(quantity).reservedQuantity(reserved)
                .updatedAt(Instant.now()).version(1)
                .build();
    }

    @Nested
    @DisplayName("expireReservations")
    class ExpireReservations {

        @Test
        @DisplayName("Should expire pending reservations and release stock")
        void shouldExpireAndRelease() {
            UUID orderId = UUID.randomUUID();
            StockReservation reservation = StockReservation.builder()
                    .id(UUID.randomUUID()).sku(SKU).orderId(orderId).quantity(5)
                    .expiresAt(Instant.now().minusSeconds(60))
                    .build();

            when(stockReservationRepository.findExpiredPending(any(Instant.class)))
                    .thenReturn(Flux.just(reservation));
            when(reservationExpirationProcessor.expireSingleReservation(reservation))
                    .thenReturn(Mono.empty());

            StepVerifier.create(useCase.expireReservations())
                    .verifyComplete();

            verify(reservationExpirationProcessor).expireSingleReservation(reservation);
        }

        @Test
        @DisplayName("Should do nothing when no expired reservations")
        void shouldDoNothingWhenNone() {
            when(stockReservationRepository.findExpiredPending(any(Instant.class)))
                    .thenReturn(Flux.empty());

            StepVerifier.create(useCase.expireReservations())
                    .verifyComplete();

            verify(reservationExpirationProcessor, never()).expireSingleReservation(any());
        }
    }

    @Nested
    @DisplayName("processOrderCancelled")
    class ProcessOrderCancelled {

        @Test
        @DisplayName("Should release reservation on order cancellation")
        void shouldReleaseReservation() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            StockReservation reservation = StockReservation.builder()
                    .id(UUID.randomUUID()).sku(SKU).orderId(orderId).quantity(10)
                    .build();
            Stock stock = buildStock(100, 30);

            when(processedEventRepository.exists(eventId)).thenReturn(Mono.just(false));
            when(stockReservationRepository.findBySkuAndOrderIdAndStatus(SKU, orderId, ReservationStatus.PENDING))
                    .thenReturn(Mono.just(reservation));
            when(stockReservationRepository.updateStatus(reservation.id(), ReservationStatus.RELEASED))
                    .thenReturn(Mono.just(reservation.release()));
            when(stockRepository.findBySku(SKU)).thenReturn(Mono.just(stock));
            when(stockRepository.updateReservedQuantity(eq(SKU), eq(20)))
                    .thenReturn(Mono.just(stock.releaseReservation(10)));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(outboxEventRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(processedEventRepository.save(eventId)).thenReturn(Mono.empty());

            StepVerifier.create(useCase.processOrderCancelled(eventId, orderId, SKU))
                    .verifyComplete();

            verify(stockReservationRepository).updateStatus(reservation.id(), ReservationStatus.RELEASED);
            verify(stockRepository).updateReservedQuantity(SKU, 20);
        }

        @Test
        @DisplayName("Should skip already processed event")
        void shouldSkipDuplicate() {
            UUID eventId = UUID.randomUUID();
            when(processedEventRepository.exists(eventId)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase.processOrderCancelled(eventId, UUID.randomUUID(), SKU))
                    .verifyComplete();

            verify(stockReservationRepository, never()).findBySkuAndOrderIdAndStatus(anyString(), any(UUID.class), any(ReservationStatus.class));
        }

        @Test
        @DisplayName("Should do nothing when no pending reservation for order")
        void shouldIgnoreWhenNoPendingReservation() {
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            when(processedEventRepository.exists(eventId)).thenReturn(Mono.just(false));
            when(stockReservationRepository.findBySkuAndOrderIdAndStatus(SKU, orderId, ReservationStatus.PENDING))
                    .thenReturn(Mono.empty());
            when(processedEventRepository.save(eventId)).thenReturn(Mono.empty());

            StepVerifier.create(useCase.processOrderCancelled(eventId, orderId, SKU))
                    .verifyComplete();

            verify(stockRepository, never()).updateReservedQuantity(anyString(), anyInt());
        }
    }
}
