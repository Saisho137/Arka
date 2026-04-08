package com.arka.api.mapper;

import com.arka.api.dto.StockResponse;
import com.arka.model.stock.Stock;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StockMapper {

    public static StockResponse toResponse(Stock stock) {
        return StockResponse.builder()
                .id(stock.id())
                .sku(stock.sku())
                .productId(stock.productId())
                .quantity(stock.quantity())
                .reservedQuantity(stock.reservedQuantity())
                .availableQuantity(stock.availableQuantity())
                .version(stock.version())
                .updatedAt(stock.updatedAt())
                .build();
    }
}
