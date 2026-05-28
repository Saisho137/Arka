package com.arka.r2dbc.payment;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface SpringDataPaymentRepository extends R2dbcRepository<PaymentDTO, UUID> {
    Mono<PaymentDTO> findByOrderId(UUID orderId);
}
