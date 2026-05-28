package com.arka.usecase.assignsupplierproduct;

import com.arka.model.commons.exception.SupplierNotFoundException;
import com.arka.model.gateways.SupplierProductRepository;
import com.arka.model.gateways.SupplierRepository;
import com.arka.model.supplier.SupplierProduct;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class AssignSupplierProductUseCase {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;

    public Mono<SupplierProduct> execute(UUID supplierId, SupplierProduct product) {
        return supplierRepository.findById(supplierId)
                .switchIfEmpty(Mono.error(new SupplierNotFoundException(supplierId.toString())))
                .flatMap(supplier -> {
                    SupplierProduct toSave = product.toBuilder().supplierId(supplierId).build();
                    if (toSave.preferred()) {
                        return supplierProductRepository.removePreferredBySku(toSave.sku())
                                .then(supplierProductRepository.save(toSave));
                    }
                    return supplierProductRepository.save(toSave);
                });
    }

    public Mono<Void> remove(UUID supplierId, String sku) {
        return supplierRepository.findById(supplierId)
                .switchIfEmpty(Mono.error(new SupplierNotFoundException(supplierId.toString())))
                .flatMap(supplier -> supplierProductRepository.deleteBySupplierIdAndSku(supplierId, sku));
    }
}
