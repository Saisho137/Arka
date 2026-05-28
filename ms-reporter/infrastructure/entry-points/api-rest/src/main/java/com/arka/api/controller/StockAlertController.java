package com.arka.api.controller;

import com.arka.api.dto.StockAlertResponse;
import com.arka.api.handler.StockAlertHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Stock Alerts")
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class StockAlertController {

    private final StockAlertHandler stockAlertHandler;

    @Operation(summary = "Get active low-stock alerts (SKUs projected to deplete within 7 days)")
    @ApiResponse(responseCode = "200", description = "List of low-stock alerts")
    @GetMapping("/low-stock")
    public ResponseEntity<List<StockAlertResponse>> getLowStock(
            @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        return stockAlertHandler.getLowStockAlerts(userRole);
    }
}
