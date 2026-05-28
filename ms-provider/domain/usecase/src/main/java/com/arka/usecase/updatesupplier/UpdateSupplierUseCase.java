package com.arka.usecase.updatesupplier;

import com.arka.model.commons.exception.SupplierNotFoundException;
import com.arka.model.gateways.SupplierRepository;
import com.arka.model.supplier.Supplier;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class UpdateSupplierUseCase {

    private final SupplierRepository supplierRepository;

    public Mono<Supplier> execute(UUID supplierId, Supplier updatedData) {
        return supplierRepository.findById(supplierId)
                .switchIfEmpty(Mono.error(new SupplierNotFoundException(supplierId.toString())))
                .flatMap(existing -> {
                    Supplier updated = existing.toBuilder()
                            .name(updatedData.name() != null ? updatedData.name() : existing.name())
                            .phone(updatedData.phone() != null ? updatedData.phone() : existing.phone())
                            .address(updatedData.address() != null ? updatedData.address() : existing.address())
                            .country(updatedData.country() != null ? updatedData.country() : existing.country())
                            .updatedAt(Instant.now())
                            .build();
                    return supplierRepository.save(updated);
                });
    }
}
