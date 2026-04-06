package com.arka.r2dbc.stockmovement;

import com.arka.model.stockmovement.StockMovement;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StockMovementDTOMapper {

    static StockMovement toDomain(StockMovementDTO data) {
        return StockMovement.builder()
                .id(data.id())
                .sku(data.sku())
                .movementType(data.movementType())
                .quantityChange(data.quantityChange())
                .previousQuantity(data.previousQuantity())
                .newQuantity(data.newQuantity())
                .orderId(data.orderId())
                .reason(data.reason())
                .createdAt(data.createdAt())
                .build();
    }

    static StockMovementDTO toDTO(StockMovement domain) {
        return StockMovementDTO.builder()
                .id(domain.id())
                .sku(domain.sku())
                .movementType(domain.movementType())
                .quantityChange(domain.quantityChange())
                .previousQuantity(domain.previousQuantity())
                .newQuantity(domain.newQuantity())
                .orderId(domain.orderId())
                .reason(domain.reason())
                .createdAt(domain.createdAt())
                .build();
    }
}
