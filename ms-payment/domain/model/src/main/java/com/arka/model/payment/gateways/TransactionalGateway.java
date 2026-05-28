package com.arka.model.payment.gateways;

import reactor.core.publisher.Mono;

public interface TransactionalGateway {
    <T> Mono<T> executeInTransaction(Mono<T> pipeline);
}
