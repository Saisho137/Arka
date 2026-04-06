package com.arka.usecase.stockreservation;

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

    // --- Expiración de reservas (job periódico cada 60s) ---

    public Mono<Void> expireReservations() {
        return stockReservationRepository.findExpiredPending(Instant.now())
                .flatMap(this::expireSingleReservation)
                .then();
    }

    private Mono<Void> expireSingleReservation(StockReservation reservation) {
        return stockReservationRepository.updateStatus(reservation.id(), ReservationStatus.EXPIRED)
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
    }

    // --- Consumidor Kafka: OrderCancelled ---

    public Mono<Void> processOrderCancelled(UUID eventId, UUID orderId, String sku) {
        return processedEventRepository.exists(eventId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        return Mono.empty();
                    }
                    return stockReservationRepository
                            .findBySkuAndOrderIdAndStatus(sku, orderId, ReservationStatus.PENDING)
                            .flatMap(reservation -> releaseReservation(reservation, "ORDER_CANCELLED"))
                            .then(processedEventRepository.save(eventId));
                });
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
