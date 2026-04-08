package com.arka.usecase.stock;

import com.arka.model.commons.exception.OptimisticLockException;
import com.arka.model.commons.exception.StockNotFoundException;
import com.arka.model.commons.gateways.TransactionalGateway;
import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.StockDepletedPayload;
import com.arka.model.outboxevent.StockReservedPayload;
import com.arka.model.outboxevent.StockReserveFailedPayload;
import com.arka.model.outboxevent.StockUpdatedPayload;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.stock.Stock;
import com.arka.model.stock.gateways.StockRepository;
import com.arka.model.stockmovement.StockMovement;
import com.arka.model.stockmovement.gateways.StockMovementRepository;
import com.arka.model.stockreservation.ReservationStatus;
import com.arka.model.stockreservation.StockReservation;
import com.arka.model.stockreservation.gateways.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class StockUseCase {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final StockReservationRepository stockReservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final JsonSerializer jsonSerializer;
    private final TransactionalGateway transactionalGateway;

    // --- Consultas (Caso A: sin transacción explícita, lectura simple) ---

    public Mono<Stock> getBySku(String sku) {
        return stockRepository.findBySku(sku)
                .switchIfEmpty(Mono.error(new StockNotFoundException(sku)));
    }

    public Flux<StockMovement> getHistory(String sku, int page, int size) {
        return stockMovementRepository.findBySkuOrderByCreatedAtDesc(sku, page, size);
    }

    // --- Actualización manual (lock optimista, Caso B: TransactionalGateway) ---

    public Mono<Stock> updateStock(String sku, int newQuantity, String reason) {
        Mono<Stock> pipeline = stockRepository.findBySku(sku)
                .switchIfEmpty(Mono.error(new StockNotFoundException(sku)))
                .flatMap(stock -> {
                    Stock updated = stock.setQuantity(newQuantity);
                    return stockRepository.updateQuantity(sku, newQuantity, stock.version())
                            .switchIfEmpty(Mono.error(new OptimisticLockException(sku)))
                            .flatMap(saved -> {
                                StockMovement movement = newQuantity > stock.quantity()
                                        ? StockMovement.restock(sku, stock.quantity(), newQuantity, reason)
                                        : StockMovement.shrinkage(sku, stock.quantity(), newQuantity, reason);

                                OutboxEvent stockUpdatedEvent = buildOutboxEvent(
                                        EventType.STOCK_UPDATED, sku,
                                        StockUpdatedPayload.builder()
                                                .sku(sku)
                                                .previousQuantity(stock.quantity())
                                                .newQuantity(newQuantity)
                                                .movementType(movement.movementType().name())
                                                .build());

                                Mono<Void> saveMovementAndEvent = stockMovementRepository.save(movement)
                                        .then(outboxEventRepository.save(stockUpdatedEvent))
                                        .then();

                                Mono<Void> depletedEvent = emitStockDepletedIfNeeded(saved);

                                return saveMovementAndEvent.then(depletedEvent).thenReturn(saved);
                            });
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // --- Reserva de stock (lock pesimista, Caso B: TransactionalGateway) ---

    public Mono<ReserveStockResult> reserveStock(String sku, UUID orderId, int quantity) {
        Mono<ReserveStockResult> pipeline = stockRepository.findBySkuForUpdate(sku)
                .switchIfEmpty(Mono.error(new StockNotFoundException(sku)))
                .flatMap(stock -> stockReservationRepository
                        .findBySkuAndOrderIdAndStatus(sku, orderId, ReservationStatus.PENDING)
                        .flatMap(existing -> Mono.just(ReserveStockResult.builder()
                                .success(true)
                                .reservationId(existing.id())
                                .availableQuantity(stock.availableQuantity())
                                .build()))
                        .switchIfEmpty(Mono.defer(() -> executeReservation(stock, sku, orderId, quantity))));

        return transactionalGateway.executeInTransaction(pipeline);
    }

    private Mono<ReserveStockResult> executeReservation(Stock stock, String sku, UUID orderId, int quantity) {
        if (!stock.canReserve(quantity)) {
            return emitReserveFailedEvent(sku, orderId, quantity, stock.availableQuantity())
                    .thenReturn(ReserveStockResult.builder()
                            .success(false)
                            .availableQuantity(stock.availableQuantity())
                            .reason("Insufficient stock for SKU: " + sku
                                    + ". Requested: " + quantity + ", Available: " + stock.availableQuantity())
                            .build());
        }

        Stock reserved = stock.reserve(quantity);
        StockReservation reservation = StockReservation.builder()
                .id(UUID.randomUUID())
                .sku(sku)
                .orderId(orderId)
                .quantity(quantity)
                .build();

        StockMovement movement = StockMovement.orderReserve(sku, quantity, stock.availableQuantity(), orderId);

        OutboxEvent reservedEvent = buildOutboxEvent(
                EventType.STOCK_RESERVED, sku,
                StockReservedPayload.builder()
                        .sku(sku).orderId(orderId).quantity(quantity).reservationId(reservation.id())
                        .build());

        return stockRepository.updateReservedQuantity(sku, reserved.reservedQuantity())
                .then(stockReservationRepository.save(reservation))
                .then(stockMovementRepository.save(movement))
                .then(outboxEventRepository.save(reservedEvent))
                .then(emitStockDepletedIfNeeded(reserved))
                .thenReturn(ReserveStockResult.builder()
                        .success(true)
                        .reservationId(reservation.id())
                        .availableQuantity(reserved.availableQuantity())
                        .build());
    }

    private Mono<Void> emitReserveFailedEvent(String sku, UUID orderId, int requested, int available) {
        OutboxEvent failedEvent = buildOutboxEvent(
                EventType.STOCK_RESERVE_FAILED, sku,
                StockReserveFailedPayload.builder()
                        .sku(sku).orderId(orderId).requestedQuantity(requested)
                        .availableQuantity(available)
                        .reason("Insufficient stock for SKU: " + sku)
                        .build());
        return outboxEventRepository.save(failedEvent).then();
    }

    // --- Consumidor Kafka: ProductCreated (Caso B: TransactionalGateway) ---

    public Mono<Void> processProductCreated(UUID eventId, String sku, UUID productId, int initialStock, int depletionThreshold) {
        Mono<Void> pipeline = processedEventRepository.exists(eventId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        return Mono.empty();
                    }
                    Stock newStock = Stock.builder()
                            .id(UUID.randomUUID())
                            .sku(sku)
                            .productId(productId)
                            .quantity(initialStock)
                            .reservedQuantity(0)
                            .depletionThreshold(depletionThreshold)
                            .build();

                    StockMovement movement = StockMovement.productCreation(sku, initialStock);

                    return stockRepository.save(newStock)
                            .then(stockMovementRepository.save(movement))
                            .then(processedEventRepository.save(eventId));
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // --- Helpers privados ---

    private Mono<Void> emitStockDepletedIfNeeded(Stock stock) {
        if (stock.isBelowThreshold()) {
            OutboxEvent depletedEvent = buildOutboxEvent(
                    EventType.STOCK_DEPLETED, stock.sku(),
                    StockDepletedPayload.builder()
                            .sku(stock.sku())
                            .currentQuantity(stock.availableQuantity())
                            .threshold(stock.depletionThreshold())
                            .build());
            return outboxEventRepository.save(depletedEvent).then();
        }
        return Mono.empty();
    }

    private OutboxEvent buildOutboxEvent(EventType eventType, String partitionKey, Object payload) {
        return OutboxEvent.builder()
                .eventType(eventType)
                .payload(jsonSerializer.serialize(payload))
                .partitionKey(partitionKey)
                .build();
    }
}
