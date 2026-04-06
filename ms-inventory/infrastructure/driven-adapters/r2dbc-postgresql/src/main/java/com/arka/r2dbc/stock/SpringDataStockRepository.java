package com.arka.r2dbc.stock;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SpringDataStockRepository extends ReactiveCrudRepository<StockDTO, UUID> {

    Mono<StockDTO> findBySku(String sku);
}
