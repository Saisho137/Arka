package com.arka.api.controller;

import com.arka.api.dto.StockMovementResponse;
import com.arka.api.dto.StockResponse;
import com.arka.api.dto.UpdateStockRequest;
import com.arka.api.handler.StockHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class StockController {

    private static final int MAX_PAGE_SIZE = 100;

    private final StockHandler stockHandler;

    @PutMapping("/{sku}/stock")
    @Operation(summary = "Update stock quantity for a SKU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "SKU not found"),
            @ApiResponse(responseCode = "409", description = "Conflict — quantity below reserved or concurrent modification")
    })
    public Mono<ResponseEntity<StockResponse>> updateStock(
            @PathVariable("sku") String sku,
            @Valid @RequestBody UpdateStockRequest request) {
        return stockHandler.updateStock(sku, request.quantity(), request.reason());
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Get stock availability for a SKU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock found"),
            @ApiResponse(responseCode = "404", description = "SKU not found")
    })
    public Mono<ResponseEntity<StockResponse>> getStock(@PathVariable("sku") String sku) {
        return stockHandler.getStock(sku);
    }

    @GetMapping("/{sku}/history")
    @Operation(summary = "Get stock movement history for a SKU")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Movement history returned"),
            @ApiResponse(responseCode = "404", description = "SKU not found")
    })
    public Flux<StockMovementResponse> getHistory(
            @PathVariable("sku") String sku,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return stockHandler.getHistory(sku, page, Math.min(size, MAX_PAGE_SIZE));
    }
}
