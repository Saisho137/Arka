package com.arka.model.commons.gateways;

import reactor.core.publisher.Mono;

/**
 * Port de dominio para ejecución transaccional.
 * El UseCase arma el pipeline reactivo con su lógica de negocio
 * y lo delega a este gateway para que la infraestructura lo envuelva
 * en una transacción de base de datos.
 */
public interface TransactionalGateway {
    <T> Mono<T> executeInTransaction(Mono<T> pipeline);
}
