package com.arka.r2dbc.stock;

import com.arka.model.stock.Stock;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StockDTOMapper {

    static Stock toDomain(StockDTO data) {
        return Stock.builder()
                .id(data.id())
                .sku(data.sku())
                .productId(data.productId())
                .quantity(data.quantity())
                .reservedQuantity(data.reservedQuantity())
                .depletionThreshold(data.depletionThreshold())
                .updatedAt(data.updatedAt())
                .version(data.version())
                .build();
    }

    static StockDTO toDTO(Stock domain) {
        return StockDTO.builder()
                .id(domain.id())
                .sku(domain.sku())
                .productId(domain.productId())
                .quantity(domain.quantity())
                .reservedQuantity(domain.reservedQuantity())
                .depletionThreshold(domain.depletionThreshold())
                .updatedAt(domain.updatedAt())
                .version(domain.version())
                .build();
    }
}
