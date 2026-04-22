package com.arka.model.payment.gateways;

import com.arka.model.payment.event.DomainEventEnvelope;
import reactor.core.publisher.Mono;

public interface PaymentEventPublisher {
    Mono<Void> publishPaymentEvent(String orderId, DomainEventEnvelope envelope);
}
