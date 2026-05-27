package com.arka.api.handler;

import com.arka.api.dto.KpiResponse;
import com.arka.api.dto.TopSellingProductResponse;
import com.arka.model.commons.exception.AccessDeniedException;
import com.arka.model.kpi.KpiResult;
import com.arka.usecase.kpicalculation.KpiCalculationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class MetricsHandler {

    private final KpiCalculationUseCase kpiCalculationUseCase;

    public ResponseEntity<KpiResponse> getKpis(LocalDate startDate, LocalDate endDate, String userRole) {
        validateAdminRole(userRole);

        KpiResult result = kpiCalculationUseCase.calculateKpis(startDate, endDate);

        KpiResponse response = KpiResponse.builder()
                .totalRevenue(result.totalRevenue())
                .totalOrders(result.totalOrders())
                .averageOrderValue(result.averageOrderValue())
                .conversionRate(result.conversionRate())
                .cartAbandonmentRate(result.cartAbandonmentRate())
                .averageDeliveryTimeDays(result.averageDeliveryTimeDays())
                .topSellingProducts(result.topSellingProducts() != null
                        ? result.topSellingProducts().stream()
                                .map(p -> TopSellingProductResponse.builder()
                                        .sku(p.sku())
                                        .productName(p.productName())
                                        .totalQuantity(p.totalQuantity())
                                        .totalRevenue(p.totalRevenue())
                                        .build())
                                .toList()
                        : null)
                .build();

        return ResponseEntity.ok(response);
    }

    private void validateAdminRole(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Only ADMIN role can access metrics");
        }
    }
}
