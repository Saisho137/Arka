package com.arka.usecase.stockreservation;

import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.StockReleasedPayload;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.stock.gateways.StockRepository;
import com.arka.model.stockmovement.StockMovement;
import com.arka.model.stockmovement.gateways.StockMovementRepository;
import com.arka.model.stockreservation.ReservationStatus;
import com.arka.model.stockreservation.StockReservation;
import com.arka.model.stockreservation.gateways.StockReservationRepository;
import com.arka.usecase.stock.JsonSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReservationExpirationProcessor {

    private final StockReservationRepository stockReservationRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonSerializer jsonSerializer;

    @Transactional
    public Mono<Void> expireSingleReservation(StockReservation reservation) {
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

                    OutboxEvent event = OutboxEvent.builder()
                            .eventType(EventType.STOCK_RELEASED)
                            .payload(jsonSerializer.serialize(StockReleasedPayload.builder()
                                    .sku(reservation.sku())
                                    .orderId(reservation.orderId())
                                    .quantity(reservation.quantity())
                                    .reason("RESERVATION_EXPIRED")
                                    .build()))
                            .partitionKey(reservation.sku())
                            .build();

                    return stockMovementRepository.save(movement)
                            .then(outboxEventRepository.save(event))
                            .then();
                });
    }
}
