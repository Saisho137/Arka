package com.arka.r2dbc.payment;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("payments")
public record PaymentDTO(
        @Id UUID id,
        @Column("order_id") UUID orderId,
        String gateway,
        @Column("transaction_id") String transactionId,
        BigDecimal amount,
        String currency,
        String status,
        @Column("failure_reason") String failureReason,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {}
