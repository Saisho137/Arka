package com.arka.model.stockreservation;

import lombok.Builder;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Builder(toBuilder = true)
public record StockReservation(
        UUID id,
        String sku,
        UUID orderId,
        int quantity,
        ReservationStatus status,
        Instant createdAt,
        Instant expiresAt
) {
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    public StockReservation {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(orderId, "orderId is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        status = status != null ? status : ReservationStatus.PENDING;
        createdAt = createdAt != null ? createdAt : Instant.now();
        expiresAt = expiresAt != null ? expiresAt : Instant.now().plus(DEFAULT_TTL);
    }

    public boolean isExpired(Instant now) {
        return status == ReservationStatus.PENDING && now.isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == ReservationStatus.PENDING;
    }

    public StockReservation expire() {
        assertPending("expire");
        return this.toBuilder().status(ReservationStatus.EXPIRED).build();
    }

    public StockReservation release() {
        assertPending("release");
        return this.toBuilder().status(ReservationStatus.RELEASED).build();
    }

    public StockReservation confirm() {
        assertPending("confirm");
        return this.toBuilder().status(ReservationStatus.CONFIRMED).build();
    }

    private void assertPending(String operation) {
        if (status != ReservationStatus.PENDING)
            throw new IllegalStateException(
                    "Cannot " + operation + " reservation " + id + " for SKU: " + sku
                            + ". Current status: " + status + ", expected: PENDING");

    }
}
