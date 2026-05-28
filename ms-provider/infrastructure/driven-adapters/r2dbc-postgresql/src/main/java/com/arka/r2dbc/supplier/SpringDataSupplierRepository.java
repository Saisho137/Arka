package com.arka.r2dbc.supplier;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SpringDataSupplierRepository extends ReactiveCrudRepository<SupplierEntity, UUID> {

    Mono<SupplierEntity> findByEmail(String email);

    @Query("SELECT * FROM suppliers WHERE active = true ORDER BY name LIMIT :size OFFSET :offset")
    Flux<SupplierEntity> findAllActive(int size, long offset);

    @Query("SELECT COUNT(*) FROM suppliers WHERE active = true")
    Mono<Long> countActive();

    @Modifying
    @Query("UPDATE suppliers SET active = false, updated_at = NOW() WHERE id = :id")
    Mono<Integer> deactivate(UUID id);
}
