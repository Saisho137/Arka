package com.arka.r2dbc.supplier;

import com.arka.model.gateways.SupplierRepository;
import com.arka.model.supplier.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcSupplierAdapter implements SupplierRepository {

    private final SpringDataSupplierRepository repository;

    @Override
    public Mono<Supplier> save(Supplier supplier) {
        return repository.save(SupplierMapper.toEntity(supplier))
                .map(SupplierMapper::toDomain);
    }

    @Override
    public Mono<Supplier> findById(UUID id) {
        return repository.findById(id)
                .map(SupplierMapper::toDomain);
    }

    @Override
    public Flux<Supplier> findAllActive(int page, int size) {
        long offset = (long) page * size;
        return repository.findAllActive(size, offset)
                .map(SupplierMapper::toDomain);
    }

    @Override
    public Mono<Long> countActive() {
        return repository.countActive();
    }

    @Override
    public Mono<Supplier> findByEmail(String email) {
        return repository.findByEmail(email)
                .map(SupplierMapper::toDomain);
    }

    @Override
    public Mono<Void> deactivate(UUID id) {
        return repository.deactivate(id).then();
    }
}
