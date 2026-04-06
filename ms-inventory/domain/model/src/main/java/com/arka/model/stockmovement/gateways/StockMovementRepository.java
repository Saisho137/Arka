package com.arka.model.stockmovement.gateways;

import com.arka.model.stockmovement.StockMovement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StockMovementRepository {

    Mono<StockMovement> save(StockMovement movement);

    Flux<StockMovement> findBySkuOrderByCreatedAtDesc(String sku, int page, int size);
}
