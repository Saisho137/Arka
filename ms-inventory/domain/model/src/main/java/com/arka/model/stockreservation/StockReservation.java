package com.arka.model.stockreservation;

import com.arka.valueobjects.ReservationStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder(toBuilder = true)
public record StockReservation(
        UUID reservationId,
        UUID sku,
        UUID orderId,
        int reservedQuantity,
        ReservationStatus status,
        LocalDateTime expiresAt
) {
    public StockReservation {
        if (reservationId == null) throw new IllegalArgumentException("ReservationId is required");
        if (sku == null) throw new IllegalArgumentException("ProductId is required");
        if (orderId == null) throw new IllegalArgumentException("OrderId is required");
        if (reservedQuantity <= 0) throw new IllegalArgumentException("Reserved quantity must be greater than zero");
        if (status == null) throw new IllegalArgumentException("Status is required");
        if (expiresAt == null) throw new IllegalArgumentException("ExpiresAt is required");
    }

    // Domain Logic Example: Check if reservation is expired
    public boolean isExpired(LocalDateTime currentTime) {
        return status == ReservationStatus.PENDING && currentTime.isAfter(expiresAt);
    }
}
