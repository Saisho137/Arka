package com.arka.model.idempotency.gateways;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IdempotencyRepository {

    Mono<Boolean> exists(UUID idempotencyKey);

    Mono<Void> save(UUID idempotencyKey);
}
