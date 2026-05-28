package com.arka.api.handler;

import com.arka.api.dto.StockMovementResponse;
import com.arka.api.dto.StockResponse;
import com.arka.api.mapper.StockMapper;
import com.arka.api.mapper.StockMovementMapper;
import com.arka.usecase.stock.StockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StockHandler {

    private final StockUseCase stockUseCase;

    public Mono<ResponseEntity<StockResponse>> getStock(String sku) {
        if (sku == null || sku.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return stockUseCase.getBySku(sku)
                .map(StockMapper::toResponse)
                .map(ResponseEntity::ok);
    }

    public Mono<ResponseEntity<StockResponse>> updateStock(String sku, int quantity, String reason) {
        if (sku == null || sku.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return stockUseCase.updateStock(sku, quantity, reason)
                .map(StockMapper::toResponse)
                .map(ResponseEntity::ok);
    }

    public Flux<StockMovementResponse> getHistory(String sku, int page, int size) {
        if (sku == null || sku.isBlank()) {
            return Flux.empty();
        }
        int validatedPage = Math.max(0, page);
        int validatedSize = Math.max(1, Math.min(size, 100));
        return stockUseCase.getHistory(sku, validatedPage, validatedSize)
                .map(StockMovementMapper::toResponse);
    }
}
