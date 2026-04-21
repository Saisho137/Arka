package com.arka.r2dbc.stockreservation;

import com.arka.model.stockreservation.ReservationStatus;
import com.arka.model.stockreservation.StockReservation;
import com.arka.model.stockreservation.gateways.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcStockReservationAdapter implements StockReservationRepository {

    private final SpringDataStockReservationRepository repository;
    private final DatabaseClient client;

    @Override
    public Mono<StockReservation> save(StockReservation reservation) {
        return repository.save(StockReservationDTOMapper.toDTO(reservation))
                .map(StockReservationDTOMapper::toDomain);
    }

    @Override
    public Mono<StockReservation> findBySkuAndOrderIdAndStatus(String sku, UUID orderId,
                                                                ReservationStatus status) {
        return repository.findBySkuAndOrderIdAndStatus(sku, orderId, status)
                .map(StockReservationDTOMapper::toDomain);
    }

    @Override
    public Flux<StockReservation> findAllByOrderIdAndStatus(UUID orderId, ReservationStatus status) {
        return repository.findAllByOrderIdAndStatus(orderId, status)
                .map(StockReservationDTOMapper::toDomain);
    }

    @Override
    public Flux<StockReservation> findExpiredPending(Instant now) {
        return repository.findExpiredPending(now)
                .map(StockReservationDTOMapper::toDomain);
    }

    @Override
    public Mono<StockReservation> updateStatus(UUID id, ReservationStatus status) {
        return client.sql("UPDATE stock_reservations SET status = :status::reservation_status WHERE id = :id")
                .bind("status", status.name())
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then(repository.findById(id))
                .map(StockReservationDTOMapper::toDomain);
    }
}
