package com.arka.usecase.stock;

import com.arka.model.commons.exception.InvalidStockQuantityException;
import com.arka.model.commons.exception.OptimisticLockException;
import com.arka.model.commons.exception.StockNotFoundException;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.stock.Stock;
import com.arka.model.stock.gateways.StockRepository;
import com.arka.model.stockmovement.MovementType;
import com.arka.model.stockmovement.StockMovement;
import com.arka.model.stockmovement.gateways.StockMovementRepository;
import com.arka.model.stockreservation.ReservationStatus;
import com.arka.model.stockreservation.StockReservation;
import com.arka.model.stockreservation.gateways.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockUseCaseTest {

    @Mock private StockRepository stockRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private StockReservationRepository stockReservationRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private JsonSerializer jsonSerializer;

    @InjectMocks
    private StockUseCase useCase;

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
                .depletionThreshold(10)
                .updatedAt(Instant.now()).version(1)
                .build();
    }

    @Nested
    @DisplayName("getBySku")
    class GetBySku {

        @Test
        @DisplayName("Should return stock when SKU exists")
        void shouldReturnStock() {
            Stock stock = buildStock(100, 10);
            when(stockRepository.findBySku(SKU)).thenReturn(Mono.just(stock));

            StepVerifier.create(useCase.getBySku(SKU))
                    .expectNextMatches(s -> s.sku().equals(SKU) && s.availableQuantity() == 90)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should throw StockNotFoundException when SKU does not exist")
        void shouldThrowNotFound() {
            when(stockRepository.findBySku("UNKNOWN")).thenReturn(Mono.empty());

            StepVerifier.create(useCase.getBySku("UNKNOWN"))
                    .expectError(StockNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("getHistory")
    class GetHistory {

        @Test
        @DisplayName("Should return movements from repository")
        void shouldReturnMovements() {
            var m1 = StockMovement.restock(SKU, 50, 100, "restock");
            var m2 = StockMovement.shrinkage(SKU, 100, 90, "damaged");
            when(stockMovementRepository.findBySkuOrderByCreatedAtDesc(SKU, 0, 10))
                    .thenReturn(Flux.just(m1, m2));

            StepVerifier.create(useCase.getHistory(SKU, 0, 10))
                    .expectNextMatches(m -> m.movementType() == MovementType.RESTOCK)
                    .expectNextMatches(m -> m.movementType() == MovementType.SHRINKAGE)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty when no movements")
        void shouldReturnEmpty() {
            when(stockMovementRepository.findBySkuOrderByCreatedAtDesc(SKU, 0, 10))
                    .thenReturn(Flux.empty());

            StepVerifier.create(useCase.getHistory(SKU, 0, 10))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateStock")
    class UpdateStock {

        @Test
        @DisplayName("Should update stock and emit StockUpdated event")
        void shouldUpdateAndEmitEvent() {
            Stock stock = buildStock(50, 0);
            Stock saved = stock.setQuantity(100).toBuilder().version(2).build();

            when(stockRepository.findBySku(SKU)).thenReturn(Mono.just(stock));
            when(stockRepository.updateQuantity(SKU, 100, 1L)).thenReturn(Mono.just(saved));
            when(stockMovementRepository.save(any())).thenReturn(Mono.just(StockMovement.restock(SKU, 50, 100, "restock")));
            when(outboxEventRepository.save(any())).thenReturn(Mono.just(OutboxEvent.builder()
                    .eventType(com.arka.model.outboxevent.EventType.STOCK_UPDATED)
                    .payload("{}").partitionKey(SKU).build()));

            StepVerifier.create(useCase.updateStock(SKU, 100, "restock"))
                    .expectNextMatches(s -> s.quantity() == 100 && s.version() == 2)
                    .verifyComplete();

            verify(stockMovementRepository).save(any());
            verify(outboxEventRepository).save(any());
        }

        @Test
        @DisplayName("Should throw OptimisticLockException on version mismatch")
        void shouldThrowOnVersionMismatch() {
            Stock stock = buildStock(50, 0);
            when(stockRepository.findBySku(SKU)).thenReturn(Mono.just(stock));
            when(stockRepository.updateQuantity(SKU, 100, 1L)).thenReturn(Mono.empty());

            StepVerifier.create(useCase.updateStock(SKU, 100, "restock"))
                    .expectError(OptimisticLockException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should throw InvalidStockQuantityException when newQuantity < reservedQuantity")
        void shouldRejectQuantityBelowReserved() {
            Stock stock = buildStock(100, 30);
            when(stockRepository.findBySku(SKU)).thenReturn(Mono.just(stock));

            StepVerifier.create(useCase.updateStock(SKU, 20, "reduce"))
                    .expectError(InvalidStockQuantityException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should emit StockDepleted when below threshold after update")
        void shouldEmitDepletedWhenBelowThreshold() {
            Stock stock = buildStock(50, 0);
            Stock saved = stock.setQuantity(5).toBuilder().version(2).build();

            when(stockRepository.findBySku(SKU)).thenReturn(Mono.just(stock));
            when(stockRepository.updateQuantity(SKU, 5, 1L)).thenReturn(Mono.just(saved));
            when(stockMovementRepository.save(any())).thenReturn(Mono.just(StockMovement.shrinkage(SKU, 50, 5, "shrink")));
            when(outboxEventRepository.save(any())).thenReturn(Mono.just(OutboxEvent.builder()
                    .eventType(com.arka.model.outboxevent.EventType.STOCK_UPDATED)
                    .payload("{}").partitionKey(SKU).build()));

            StepVerifier.create(useCase.updateStock(SKU, 5, "shrink"))
                    .expectNextMatches(s -> s.quantity() == 5)
                    .verifyComplete();

            // StockUpdated + StockDepleted = 2 saves
            verify(outboxEventRepository, org.mockito.Mockito.times(2)).save(any());
        }
    }

    @Nested
    @DisplayName("reserveStock")
    class ReserveStock {

        @Test
        @DisplayName("Should reserve stock when available quantity is sufficient")
        void shouldReserveWhenSufficient() {
            Stock stock = buildStock(100, 0);
            UUID orderId = UUID.randomUUID();

            when(stockRepository.findBySkuForUpdate(SKU)).thenReturn(Mono.just(stock));
            when(stockReservationRepository.findBySkuAndOrderIdAndStatus(eq(SKU), eq(orderId), eq(ReservationStatus.PENDING)))
                    .thenReturn(Mono.empty());
            when(stockRepository.updateReservedQuantity(eq(SKU), eq(10))).thenReturn(Mono.just(stock.reserve(10)));
            when(stockReservationRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(outboxEventRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(useCase.reserveStock(SKU, orderId, 10))
                    .expectNextMatches(r -> r.success() && r.reservationId() != null && r.availableQuantity() == 90)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return failure when insufficient stock")
        void shouldFailWhenInsufficient() {
            Stock stock = buildStock(5, 0);
            UUID orderId = UUID.randomUUID();

            when(stockRepository.findBySkuForUpdate(SKU)).thenReturn(Mono.just(stock));
            when(stockReservationRepository.findBySkuAndOrderIdAndStatus(eq(SKU), eq(orderId), eq(ReservationStatus.PENDING)))
                    .thenReturn(Mono.empty());
            when(outboxEventRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(useCase.reserveStock(SKU, orderId, 10))
                    .expectNextMatches(r -> !r.success() && r.reason() != null)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return existing reservation for idempotent request")
        void shouldReturnExistingReservation() {
            Stock stock = buildStock(100, 10);
            UUID orderId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            StockReservation existing = StockReservation.builder()
                    .id(reservationId).sku(SKU).orderId(orderId).quantity(10).build();

            when(stockRepository.findBySkuForUpdate(SKU)).thenReturn(Mono.just(stock));
            when(stockReservationRepository.findBySkuAndOrderIdAndStatus(eq(SKU), eq(orderId), eq(ReservationStatus.PENDING)))
                    .thenReturn(Mono.just(existing));

            StepVerifier.create(useCase.reserveStock(SKU, orderId, 10))
                    .expectNextMatches(r -> r.success() && r.reservationId().equals(reservationId))
                    .verifyComplete();

            verify(stockRepository, never()).updateReservedQuantity(anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("processProductCreated")
    class ProcessProductCreated {

        @Test
        @DisplayName("Should create stock for new product")
        void shouldCreateStock() {
            UUID eventId = UUID.randomUUID();
            when(processedEventRepository.exists(eventId)).thenReturn(Mono.just(false));
            when(stockRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(stockMovementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(processedEventRepository.save(eventId)).thenReturn(Mono.empty());

            StepVerifier.create(useCase.processProductCreated(eventId, SKU, PRODUCT_ID, 50, 5))
                    .verifyComplete();

            ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
            verify(stockRepository).save(stockCaptor.capture());
            assertThat(stockCaptor.getValue().quantity()).isEqualTo(50);
            assertThat(stockCaptor.getValue().reservedQuantity()).isZero();
            assertThat(stockCaptor.getValue().depletionThreshold()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should skip already processed event")
        void shouldSkipDuplicate() {
            UUID eventId = UUID.randomUUID();
            when(processedEventRepository.exists(eventId)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase.processProductCreated(eventId, SKU, PRODUCT_ID, 50, 10))
                    .verifyComplete();

            verify(stockRepository, never()).save(any());
        }
    }
}
