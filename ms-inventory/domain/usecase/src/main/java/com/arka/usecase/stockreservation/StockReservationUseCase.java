package com.arka.usecase.stockreservation;

import com.arka.model.commons.gateways.TransactionalGateway;
import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.StockReleasedPayload;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.stock.gateways.StockRepository;
import com.arka.model.stockmovement.StockMovement;
import com.arka.model.stockmovement.gateways.StockMovementRepository;
import com.arka.model.stockreservation.ReservationStatus;
import com.arka.model.stockreservation.StockReservation;
import com.arka.model.stockreservation.gateways.StockReservationRepository;
import com.arka.usecase.stock.JsonSerializer;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class StockReservationUseCase {

    private final StockReservationRepository stockReservationRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final JsonSerializer jsonSerializer;
    private final TransactionalGateway transactionalGateway;

    // --- Expiración de reservas (job periódico cada 60s) ---
    // Cada reserva se procesa en su propia transacción para aislamiento de fallos

    public Mono<Void> expireReservations() {
        return stockReservationRepository.findExpiredPending(Instant.now())
                .flatMap(this::expireSingleReservation)
                .then();
    }

    private Mono<Void> expireSingleReservation(StockReservation reservation) {
        Mono<Void> pipeline = stockReservationRepository.updateStatus(reservation.id(), ReservationStatus.EXPIRED)
                .then(stockRepository.findBySku(reservation.sku()))
                .flatMap(stock -> {
                    var released = stock.releaseReservation(reservation.quantity());
                    return stockRepository.updateReservedQuantity(reservation.sku(), released.reservedQuantity())
                            .thenReturn(released);
                })
                .flatMap(released -> {
                    StockMovement movement = StockMovement.reservationRelease(
                            reservation.sku(), reservation.quantity(),
                            released.availableQuantity() - reservation.quantity(),
                            reservation.orderId(), "RESERVATION_EXPIRED");

                    OutboxEvent event = buildOutboxEvent(
                            EventType.STOCK_RELEASED, reservation.sku(),
                            StockReleasedPayload.builder()
                                    .sku(reservation.sku())
                                    .orderId(reservation.orderId())
                                    .quantity(reservation.quantity())
                                    .reason("RESERVATION_EXPIRED")
                                    .build());

                    return stockMovementRepository.save(movement)
                            .then(outboxEventRepository.save(event))
                            .then();
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // --- Consumidor Kafka: OrderConfirmed (Caso B: TransactionalGateway) ---

    public Mono<Void> processOrderConfirmed(UUID eventId, UUID orderId) {
        Mono<Void> pipeline = processedEventRepository.exists(eventId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        return Mono.empty();
                    }
                    return stockReservationRepository
                            .findAllByOrderIdAndStatus(orderId, ReservationStatus.PENDING)
                            .switchIfEmpty(Flux.empty())
                            .flatMap(reservation ->
                                    stockReservationRepository.updateStatus(reservation.id(), ReservationStatus.CONFIRMED)
                                            .then(stockRepository.findBySkuForUpdate(reservation.sku()))
                                            .flatMap(stock -> {
                                                var deducted = stock.commitReservation(reservation.quantity());
                                                return stockRepository.updateQuantity(reservation.sku(), deducted.quantity(), stock.version())
                                                        .then(stockRepository.updateReservedQuantity(reservation.sku(), deducted.reservedQuantity()));
                                            })
                                            .then(stockMovementRepository.save(
                                                    StockMovement.orderConfirm(reservation.sku(), reservation.quantity(), orderId)))
                            )
                            .then(processedEventRepository.save(eventId));
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // --- Consumidor Kafka: OrderCancelled (Caso B: TransactionalGateway) ---

    public Mono<Void> processOrderCancelled(UUID eventId, UUID orderId) {
        Mono<Void> pipeline = processedEventRepository.exists(eventId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        return Mono.empty();
                    }
                    return stockReservationRepository
                            .findAllByOrderIdAndStatus(orderId, ReservationStatus.PENDING)
                            .switchIfEmpty(Flux.empty())
                            .flatMap(reservation -> releaseReservation(reservation, "ORDER_CANCELLED"))
                            .then(processedEventRepository.save(eventId));
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    private Mono<Void> releaseReservation(StockReservation reservation, String reason) {
        return stockReservationRepository.updateStatus(reservation.id(), ReservationStatus.RELEASED)
                .then(stockRepository.findBySku(reservation.sku()))
                .flatMap(stock -> {
                    var released = stock.releaseReservation(reservation.quantity());
                    return stockRepository.updateReservedQuantity(reservation.sku(), released.reservedQuantity())
                            .thenReturn(released);
                })
                .flatMap(released -> {
                    StockMovement movement = StockMovement.reservationRelease(
                            reservation.sku(), reservation.quantity(),
                            released.availableQuantity() - reservation.quantity(),
                            reservation.orderId(), reason);

                    OutboxEvent event = buildOutboxEvent(
                            EventType.STOCK_RELEASED, reservation.sku(),
                            StockReleasedPayload.builder()
                                    .sku(reservation.sku())
                                    .orderId(reservation.orderId())
                                    .quantity(reservation.quantity())
                                    .reason(reason)
                                    .build());

                    return stockMovementRepository.save(movement)
                            .then(outboxEventRepository.save(event))
                            .then();
                });
    }

    private OutboxEvent buildOutboxEvent(EventType eventType, String partitionKey, Object payload) {
        return OutboxEvent.builder()
                .eventType(eventType)
                .payload(jsonSerializer.serialize(payload))
                .partitionKey(partitionKey)
                .build();
    }
}
