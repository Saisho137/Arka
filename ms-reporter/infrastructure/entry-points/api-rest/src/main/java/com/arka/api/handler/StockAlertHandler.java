package com.arka.api.handler;

import com.arka.api.dto.StockAlertResponse;
import com.arka.model.commons.exception.AccessDeniedException;
import com.arka.usecase.stockalert.StockAlertUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StockAlertHandler {

    private final StockAlertUseCase stockAlertUseCase;

    public ResponseEntity<List<StockAlertResponse>> getLowStockAlerts(String userRole) {
        validateAdminRole(userRole);

        List<StockAlertResponse> response = stockAlertUseCase.analyzeStockPatterns().stream()
                .map(alert -> StockAlertResponse.builder()
                        .id(alert.id())
                        .sku(alert.sku())
                        .productName(alert.productName())
                        .currentStock(alert.currentStock())
                        .dailyRate(alert.dailyRate())
                        .daysUntilOut(alert.daysUntilOut())
                        .alertStatus(alert.alertStatus() != null ? alert.alertStatus().name() : null)
                        .createdAt(alert.createdAt())
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }

    private void validateAdminRole(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Only ADMIN role can access stock alerts");
        }
    }
}
