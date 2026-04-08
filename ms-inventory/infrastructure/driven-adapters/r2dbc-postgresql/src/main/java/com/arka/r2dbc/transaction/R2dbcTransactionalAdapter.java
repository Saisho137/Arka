package com.arka.r2dbc.transaction;

import com.arka.model.commons.gateways.TransactionalGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcTransactionalAdapter implements TransactionalGateway {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> executeInTransaction(Mono<T> pipeline) {
        return transactionalOperator.transactional(pipeline);
    }
}
