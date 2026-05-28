package com.arka.r2dbc.supplier;

import com.arka.model.gateways.SupplierProductRepository;
import com.arka.model.supplier.SupplierProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcSupplierProductAdapter implements SupplierProductRepository {

    private final SpringDataSupplierProductRepository repository;

    @Override
    public Mono<SupplierProduct> save(SupplierProduct supplierProduct) {
        return repository.save(SupplierProductMapper.toEntity(supplierProduct))
                .map(SupplierProductMapper::toDomain);
    }

    @Override
    public Mono<SupplierProduct> findBySupplierIdAndSku(UUID supplierId, String sku) {
        return repository.findBySupplierIdAndSku(supplierId, sku)
                .map(SupplierProductMapper::toDomain);
    }

    @Override
    public Flux<SupplierProduct> findBySku(String sku) {
        return repository.findBySku(sku)
                .map(SupplierProductMapper::toDomain);
    }

    @Override
    public Mono<SupplierProduct> findPreferredBySku(String sku) {
        return repository.findPreferredBySku(sku)
                .map(SupplierProductMapper::toDomain);
    }

    @Override
    public Flux<SupplierProduct> findBySupplierId(UUID supplierId) {
        return repository.findBySupplierId(supplierId)
                .map(SupplierProductMapper::toDomain);
    }

    @Override
    public Mono<Void> deleteBySupplierIdAndSku(UUID supplierId, String sku) {
        return repository.deleteBySupplierIdAndSku(supplierId, sku).then();
    }

    @Override
    public Mono<Void> removePreferredBySku(String sku) {
        return repository.removePreferredBySku(sku).then();
    }
}
