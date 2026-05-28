package com.arka.r2dbc.payment;

import com.arka.model.payment.Payment;
import com.arka.model.payment.PaymentStatus;
import com.arka.model.payment.gateways.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class R2dbcPaymentAdapter implements PaymentRepository {

    private final SpringDataPaymentRepository repository;
    private final R2dbcEntityTemplate entityTemplate;

    @Override
    public Mono<Payment> save(Payment payment) {
        PaymentDTO dto = new PaymentDTO(
                null,
                payment.orderId(),
                payment.gateway(),
                payment.transactionId(),
                payment.amount(),
                payment.currency(),
                payment.status().name(),
                payment.failureReason(),
                Instant.now(),
                Instant.now()
        );
        return entityTemplate.insert(dto).map(this::toDomain);
    }

    @Override
    public Mono<Payment> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Payment> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Mono<Payment> updateStatus(UUID id, Payment payment) {
        return repository.findById(id)
                .flatMap(existing -> {
                    PaymentDTO updated = new PaymentDTO(
                            existing.id(),
                            existing.orderId(),
                            existing.gateway(),
                            payment.transactionId() != null ? payment.transactionId() : existing.transactionId(),
                            existing.amount(),
                            existing.currency(),
                            payment.status().name(),
                            payment.failureReason(),
                            existing.createdAt(),
                            Instant.now()
                    );
                    return repository.save(updated).map(this::toDomain);
                });
    }

    private Payment toDomain(PaymentDTO dto) {
        return Payment.builder()
                .id(dto.id())
                .orderId(dto.orderId())
                .gateway(dto.gateway())
                .transactionId(dto.transactionId())
                .amount(dto.amount())
                .currency(dto.currency())
                .status(PaymentStatus.valueOf(dto.status()))
                .failureReason(dto.failureReason())
                .createdAt(dto.createdAt())
                .updatedAt(dto.updatedAt())
                .build();
    }
}
