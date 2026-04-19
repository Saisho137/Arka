package com.arka.model.commons.gateways;

import reactor.core.publisher.Mono;

/**
 * Domain port for transactional execution.
 * The UseCase builds the reactive pipeline with its business logic
 * and delegates it to this gateway so that infrastructure wraps it
 * in a database transaction.
 */
public interface TransactionalGateway {
    <T> Mono<T> executeInTransaction(Mono<T> pipeline);
}
