package com.arka.r2dbc.stockreservation;

import com.arka.model.stockreservation.ReservationStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface SpringDataStockReservationRepository extends ReactiveCrudRepository<StockReservationDTO, UUID> {

    Mono<StockReservationDTO> findBySkuAndOrderIdAndStatus(String sku, UUID orderId, ReservationStatus status);

    @Query("SELECT * FROM stock_reservations WHERE order_id = :orderId AND status = :status::reservation_status")
    Flux<StockReservationDTO> findAllByOrderIdAndStatus(UUID orderId, ReservationStatus status);

    @Query("SELECT * FROM stock_reservations WHERE status = 'PENDING' AND expires_at < :now")
    Flux<StockReservationDTO> findExpiredPending(Instant now);
}
