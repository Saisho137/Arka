package com.arka.r2dbc.supplier;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SpringDataSupplierProductRepository extends ReactiveCrudRepository<SupplierProductEntity, UUID> {

    Flux<SupplierProductEntity> findBySku(String sku);

    @Query("SELECT * FROM supplier_products sp JOIN suppliers s ON sp.supplier_id = s.id WHERE sp.sku = :sku AND sp.preferred = true AND s.active = true LIMIT 1")
    Mono<SupplierProductEntity> findPreferredBySku(String sku);

    Mono<SupplierProductEntity> findBySupplierIdAndSku(UUID supplierId, String sku);

    Flux<SupplierProductEntity> findBySupplierId(UUID supplierId);

    @Modifying
    @Query("DELETE FROM supplier_products WHERE supplier_id = :supplierId AND sku = :sku")
    Mono<Integer> deleteBySupplierIdAndSku(UUID supplierId, String sku);

    @Modifying
    @Query("UPDATE supplier_products SET preferred = false WHERE sku = :sku AND preferred = true")
    Mono<Integer> removePreferredBySku(String sku);
}
