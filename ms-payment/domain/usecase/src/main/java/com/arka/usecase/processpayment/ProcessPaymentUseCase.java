package com.arka.usecase.processpayment;

import com.arka.model.payment.PaymentFailedPayload;
import com.arka.model.payment.PaymentProcessedPayload;
import com.arka.model.payment.PaymentStatus;
import com.arka.model.payment.event.DomainEventEnvelope;
import com.arka.model.payment.gateways.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class ProcessPaymentUseCase {

    private static final Logger log = Logger.getLogger(ProcessPaymentUseCase.class.getName());
    private static final double SUCCESS_RATE = 0.80;
    private final Random random = new Random();

    private final PaymentEventPublisher paymentEventPublisher;

    public Mono<Void> process(UUID orderId, UUID correlationId) {
        boolean success = random.nextDouble() < SUCCESS_RATE;

        DomainEventEnvelope envelope;
        if (success) {
            PaymentProcessedPayload payload = PaymentProcessedPayload.builder()
                    .orderId(orderId)
                    .transactionId("mock-txn-" + UUID.randomUUID())
                    .status(PaymentStatus.COMPLETED)
                    .build();
            envelope = DomainEventEnvelope.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("PaymentProcessed")
                    .correlationId(orderId.toString())
                    .payload(payload)
                    .build();
        } else {
            PaymentFailedPayload payload = PaymentFailedPayload.builder()
                    .orderId(orderId)
                    .reason("Mock payment rejected (simulated 20% failure rate)")
                    .build();
            envelope = DomainEventEnvelope.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("PaymentFailed")
                    .correlationId(orderId.toString())
                    .payload(payload)
                    .build();
        }

        log.info(String.format("Processing mock payment for orderId=%s \u2192 %s", orderId, envelope.eventType()));
        return paymentEventPublisher.publishPaymentEvent(orderId.toString(), envelope);
    }
}

