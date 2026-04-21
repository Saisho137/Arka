package com.arka.r2dbc.order;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("orders")
@Builder(toBuilder = true)
public record OrderDTO(
        @Id UUID id,
        @Column("customer_id") UUID customerId,
        String status,
        @Column("total_amount") BigDecimal totalAmount,
        @Column("customer_email") String customerEmail,
        @Column("shipping_address") String shippingAddress,
        String notes,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt
) {}
