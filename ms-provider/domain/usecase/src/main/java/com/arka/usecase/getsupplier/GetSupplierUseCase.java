package com.arka.usecase.getsupplier;

import com.arka.model.commons.exception.SupplierNotFoundException;
import com.arka.model.gateways.SupplierProductRepository;
import com.arka.model.gateways.SupplierRepository;
import com.arka.model.supplier.Supplier;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class GetSupplierUseCase {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;

    public Mono<Supplier> execute(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .switchIfEmpty(Mono.error(new SupplierNotFoundException(supplierId.toString())))
                .flatMap(supplier -> supplierProductRepository.findBySupplierId(supplierId)
                        .collectList()
                        .map(products -> supplier.toBuilder().products(products).build()));
    }
}
