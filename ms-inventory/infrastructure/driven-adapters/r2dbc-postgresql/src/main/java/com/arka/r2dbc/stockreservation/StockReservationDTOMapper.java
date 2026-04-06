package com.arka.r2dbc.stockreservation;

import com.arka.model.stockreservation.StockReservation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StockReservationDTOMapper {

    static StockReservation toDomain(StockReservationDTO data) {
        return StockReservation.builder()
                .id(data.id())
                .sku(data.sku())
                .orderId(data.orderId())
                .quantity(data.quantity())
                .status(data.status())
                .createdAt(data.createdAt())
                .expiresAt(data.expiresAt())
                .build();
    }

    static StockReservationDTO toDTO(StockReservation domain) {
        return StockReservationDTO.builder()
                .id(domain.id())
                .sku(domain.sku())
                .orderId(domain.orderId())
                .quantity(domain.quantity())
                .status(domain.status())
                .createdAt(domain.createdAt())
                .expiresAt(domain.expiresAt())
                .build();
    }
}
