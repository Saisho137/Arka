package com.arka.model.gateways;

import com.arka.model.supplier.SupplierProduct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SupplierProductRepository {

    Mono<SupplierProduct> save(SupplierProduct supplierProduct);

    Mono<SupplierProduct> findBySupplierIdAndSku(UUID supplierId, String sku);

    Flux<SupplierProduct> findBySku(String sku);

    Mono<SupplierProduct> findPreferredBySku(String sku);

    Flux<SupplierProduct> findBySupplierId(UUID supplierId);

    Mono<Void> deleteBySupplierIdAndSku(UUID supplierId, String sku);

    Mono<Void> removePreferredBySku(String sku);
}
