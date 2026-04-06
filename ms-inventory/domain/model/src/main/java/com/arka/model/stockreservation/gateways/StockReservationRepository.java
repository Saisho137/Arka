package com.arka.model.stockreservation.gateways;

import com.arka.model.stockreservation.ReservationStatus;
import com.arka.model.stockreservation.StockReservation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface StockReservationRepository {

    Mono<StockReservation> save(StockReservation reservation);

    Mono<StockReservation> findBySkuAndOrderIdAndStatus(String sku, UUID orderId, ReservationStatus status);

    Flux<StockReservation> findExpiredPending(Instant now);

    Mono<StockReservation> updateStatus(UUID id, ReservationStatus status);
}
