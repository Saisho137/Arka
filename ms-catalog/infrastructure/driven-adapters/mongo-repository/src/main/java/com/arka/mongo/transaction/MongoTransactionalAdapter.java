package com.arka.mongo.transaction;

import com.arka.model.commons.gateways.TransactionalGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * MongoDB implementation of TransactionalGateway.
 * Wraps the reactive pipeline in a MongoDB multi-document transaction.
 * Requires a replica set (standalone MongoDB does not support multi-document transactions).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoTransactionalAdapter implements TransactionalGateway {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> executeInTransaction(Mono<T> pipeline) {
        return transactionalOperator.transactional(pipeline)
                .doOnSuccess(result -> log.debug("MongoDB transaction committed successfully"))
                .doOnError(error -> log.error("MongoDB transaction rolled back: {}", error.getMessage(), error));
    }
}
