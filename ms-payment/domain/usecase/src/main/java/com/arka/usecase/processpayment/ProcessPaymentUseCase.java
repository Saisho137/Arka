package com.arka.usecase.processpayment;

import com.arka.model.payment.Payment;
import com.arka.model.payment.PaymentGatewayResult;
import com.arka.model.payment.PaymentProcessedPayload;
import com.arka.model.payment.PaymentFailedPayload;
import com.arka.model.payment.PaymentStatus;
import com.arka.model.payment.event.EventType;
import com.arka.model.payment.event.OutboxEvent;
import com.arka.model.payment.gateways.JsonSerializer;
import com.arka.model.payment.gateways.OutboxEventRepository;
import com.arka.model.payment.gateways.PaymentGateway;
import com.arka.model.payment.gateways.PaymentRepository;
import com.arka.model.payment.gateways.ProcessedEventRepository;
import com.arka.model.payment.gateways.TransactionalGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.math.BigDecimal;
import java.util.UUID;

@RequiredArgsConstructor
public class ProcessPaymentUseCase {

    private static final Logger log = Loggers.getLogger(ProcessPaymentUseCase.class);

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final JsonSerializer jsonSerializer;
    private final TransactionalGateway transactionalGateway;

    public Mono<Void> process(UUID eventId, UUID orderId, BigDecimal amount, String currency) {
        Mono<Void> pipeline = processedEventRepository.exists(eventId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        return Mono.<Void>empty();
                    }

                    Payment pendingPayment = Payment.builder()
                            .id(UUID.randomUUID())
                            .orderId(orderId)
                            .gateway(paymentGateway.gatewayName())
                            .amount(amount)
                            .currency(currency != null ? currency : "COP")
                            .status(PaymentStatus.PENDING)
                            .build();

                    return paymentRepository.save(pendingPayment)
                            .flatMap(saved -> paymentGateway.charge(orderId, amount, saved.currency())
                                    .flatMap(result -> handleGatewayResult(saved, result, eventId)));
                });

        return transactionalGateway.executeInTransaction(pipeline)
                .doOnSubscribe(s -> log.info("ProcessPaymentUseCase: starting eventId={}, orderId={}, amount={}", eventId, orderId, amount))
                .doOnSuccess(v -> log.info("ProcessPaymentUseCase: completed eventId={}, orderId={}", eventId, orderId))
                .doOnError(ex -> log.error("ProcessPaymentUseCase: failed eventId={}, orderId={}", eventId, orderId, ex));
    }

    private Mono<Void> handleGatewayResult(Payment payment, PaymentGatewayResult result, UUID eventId) {
        if (result.success()) {
            Payment completed = payment.toBuilder()
                    .transactionId(result.transactionId())
                    .status(PaymentStatus.COMPLETED)
                    .build();

            PaymentProcessedPayload payload = PaymentProcessedPayload.builder()
                    .orderId(payment.orderId())
                    .transactionId(result.transactionId())
                    .status(PaymentStatus.COMPLETED)
                    .build();

            OutboxEvent outboxEvent = buildOutboxEvent(
                    EventType.PAYMENT_PROCESSED,
                    payment.orderId().toString(),
                    payload);

            return paymentRepository.updateStatus(payment.id(), completed)
                    .then(outboxEventRepository.save(outboxEvent))
                    .then(processedEventRepository.save(eventId));
        } else {
            Payment failed = payment.toBuilder()
                    .status(PaymentStatus.FAILED)
                    .failureReason(result.failureReason())
                    .build();

            PaymentFailedPayload payload = PaymentFailedPayload.builder()
                    .orderId(payment.orderId())
                    .reason(result.failureReason())
                    .build();

            OutboxEvent outboxEvent = buildOutboxEvent(
                    EventType.PAYMENT_FAILED,
                    payment.orderId().toString(),
                    payload);

            return paymentRepository.updateStatus(payment.id(), failed)
                    .then(outboxEventRepository.save(outboxEvent))
                    .then(processedEventRepository.save(eventId));
        }
    }

    private OutboxEvent buildOutboxEvent(EventType eventType, String partitionKey, Object payload) {
        return OutboxEvent.builder()
                .eventType(eventType)
                .payload(jsonSerializer.serialize(payload))
                .partitionKey(partitionKey)
                .build();
    }

    public Mono<Payment> getPaymentByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId);
    }
}

