package com.arka.usecase.deactivatesupplier;

import com.arka.model.commons.exception.SupplierNotFoundException;
import com.arka.model.gateways.SupplierRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class DeactivateSupplierUseCase {

    private final SupplierRepository supplierRepository;

    public Mono<Void> execute(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .switchIfEmpty(Mono.error(new SupplierNotFoundException(supplierId.toString())))
                .flatMap(existing -> supplierRepository.deactivate(supplierId));
    }
}
