package com.arka.model.payment.gateways;

import com.arka.model.payment.Payment;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PaymentRepository {
    Mono<Payment> save(Payment payment);
    Mono<Payment> findById(UUID id);
    Mono<Payment> findByOrderId(UUID orderId);
    Mono<Payment> updateStatus(UUID id, Payment payment);
}
