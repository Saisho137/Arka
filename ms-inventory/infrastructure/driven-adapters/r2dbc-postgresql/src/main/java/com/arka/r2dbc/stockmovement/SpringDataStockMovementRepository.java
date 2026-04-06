package com.arka.r2dbc.stockmovement;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SpringDataStockMovementRepository extends ReactiveCrudRepository<StockMovementDTO, UUID> {

    @Query("SELECT * FROM stock_movements WHERE sku = :sku ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    Flux<StockMovementDTO> findBySkuPaginated(String sku, int limit, int offset);
}
