package com.arka.api.mapper;

import com.arka.api.dto.StockMovementResponse;
import com.arka.model.stockmovement.StockMovement;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StockMovementMapper {

    public static StockMovementResponse toResponse(StockMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.id())
                .sku(movement.sku())
                .movementType(movement.movementType().name())
                .quantityChange(movement.quantityChange())
                .previousQuantity(movement.previousQuantity())
                .newQuantity(movement.newQuantity())
                .referenceId(movement.orderId())
                .reason(movement.reason())
                .createdAt(movement.createdAt())
                .build();
    }
}
