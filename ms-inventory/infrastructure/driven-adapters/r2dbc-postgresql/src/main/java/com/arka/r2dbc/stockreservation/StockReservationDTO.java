package com.arka.r2dbc.stockreservation;

import com.arka.model.stockreservation.ReservationStatus;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("stock_reservations")
@Builder(toBuilder = true)
public record StockReservationDTO(
        @Id UUID id,
        String sku,
        @Column("order_id") UUID orderId,
        int quantity,
        ReservationStatus status,
        @Column("created_at") Instant createdAt,
        @Column("expires_at") Instant expiresAt
) {}
