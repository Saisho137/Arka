package com.arka.r2dbc.transaction;

import com.arka.model.commons.gateways.TransactionalGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class R2dbcTransactionalAdapter implements TransactionalGateway {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> executeInTransaction(Mono<T> pipeline) {
        return transactionalOperator.transactional(pipeline)
                .doOnSuccess(result -> log.debug("Transaction committed successfully"))
                .doOnError(error -> log.error("Transaction rolled back due to error: {}", error.getMessage(), error));
    }
}
