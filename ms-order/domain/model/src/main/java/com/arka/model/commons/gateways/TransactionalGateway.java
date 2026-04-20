package com.arka.model.commons.gateways;

import reactor.core.publisher.Mono;

public interface TransactionalGateway {
    <T> Mono<T> executeInTransaction(Mono<T> pipeline);
}
