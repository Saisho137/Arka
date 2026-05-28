package com.arka.usecase.createsupplier;

import com.arka.model.commons.exception.DuplicateEmailException;
import com.arka.model.gateways.SupplierRepository;
import com.arka.model.supplier.Supplier;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CreateSupplierUseCase {

    private final SupplierRepository supplierRepository;

    public Mono<Supplier> execute(Supplier supplier) {
        return supplierRepository.findByEmail(supplier.email())
                .flatMap(existing -> Mono.<Supplier>error(new DuplicateEmailException(supplier.email())))
                .switchIfEmpty(Mono.defer(() -> supplierRepository.save(
                        supplier.toBuilder().active(true).build()
                )));
    }
}
