package com.arka.model.gateways;

import com.arka.model.supplier.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SupplierRepository {

    Mono<Supplier> save(Supplier supplier);

    Mono<Supplier> findById(UUID id);

    Flux<Supplier> findAllActive(int page, int size);

    Mono<Long> countActive();

    Mono<Supplier> findByEmail(String email);

    Mono<Void> deactivate(UUID id);
}
