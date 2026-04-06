package com.arka.model.stock.gateways;

import com.arka.model.stock.Stock;
import reactor.core.publisher.Mono;

public interface StockRepository {

    Mono<Stock> findBySku(String sku);

    Mono<Stock> findBySkuForUpdate(String sku);

    Mono<Stock> save(Stock stock);

    Mono<Stock> updateQuantity(String sku, int newQuantity, long expectedVersion);

    Mono<Stock> updateReservedQuantity(String sku, int newReservedQuantity);
}
