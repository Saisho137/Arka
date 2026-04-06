package com.arka.r2dbc.stock;

import com.arka.model.stock.Stock;
import com.arka.model.stock.gateways.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class R2dbcStockAdapter implements StockRepository {

    private final SpringDataStockRepository repository;
    private final DatabaseClient client;

    @Override
    public Mono<Stock> findBySku(String sku) {
        return repository.findBySku(sku)
                .map(StockDTOMapper::toDomain);
    }

    @Override
    public Mono<Stock> findBySkuForUpdate(String sku) {
        return client.sql("SELECT id, sku, product_id, quantity, reserved_quantity, " +
                        "depletion_threshold, updated_at, version " +
                        "FROM stock WHERE sku = :sku FOR UPDATE")
                .bind("sku", sku)
                .map(StockRowMapper::map)
                .one();
    }

    @Override
    public Mono<Stock> save(Stock stock) {
        return repository.save(StockDTOMapper.toDTO(stock))
                .map(StockDTOMapper::toDomain);
    }

    @Override
    public Mono<Stock> updateQuantity(String sku, int newQuantity, long expectedVersion) {
        return client.sql("UPDATE stock SET quantity = :quantity, version = version + 1, " +
                        "updated_at = NOW() WHERE sku = :sku AND version = :version")
                .bind("quantity", newQuantity)
                .bind("sku", sku)
                .bind("version", expectedVersion)
                .fetch()
                .rowsUpdated()
                .filter(rows -> rows > 0)
                .flatMap(rows -> findBySku(sku));
    }

    @Override
    public Mono<Stock> updateReservedQuantity(String sku, int newReservedQuantity) {
        return client.sql("UPDATE stock SET reserved_quantity = :reservedQuantity, " +
                        "updated_at = NOW() WHERE sku = :sku")
                .bind("reservedQuantity", newReservedQuantity)
                .bind("sku", sku)
                .fetch()
                .rowsUpdated()
                .then(findBySku(sku));
    }
}
