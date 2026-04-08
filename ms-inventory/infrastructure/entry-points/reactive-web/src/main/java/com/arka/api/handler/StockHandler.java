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
        return stockUseCase.getBySku(sku)
                .map(StockMapper::toResponse)
                .map(ResponseEntity::ok);
    }

    public Mono<ResponseEntity<StockResponse>> updateStock(String sku, int quantity, String reason) {
        return stockUseCase.updateStock(sku, quantity, reason)
                .map(StockMapper::toResponse)
                .map(ResponseEntity::ok);
    }

    public Flux<StockMovementResponse> getHistory(String sku, int page, int size) {
        return stockUseCase.getHistory(sku, page, size)
                .map(StockMovementMapper::toResponse);
    }
}
