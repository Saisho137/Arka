package com.arka.usecase.kpicalculation;

import com.arka.model.commons.exception.InvalidDateRangeException;
import com.arka.model.eventstore.gateways.EventStoreRepository;
import com.arka.model.kpi.KpiResult;
import com.arka.model.kpi.TopSellingProduct;
import com.arka.model.sales.SalesSummary;
import com.arka.model.sales.gateways.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RequiredArgsConstructor
public class KpiCalculationUseCase {

    private final EventStoreRepository eventStoreRepository;
    private final SalesSummaryRepository salesSummaryRepository;

    public KpiResult calculateKpis(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("Start date must be before or equal to end date");
        }

        Instant from = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<SalesSummary> salesData = salesSummaryRepository.findByWeekRange(startDate, endDate);

        BigDecimal totalRevenue = salesData.stream()
                .map(SalesSummary::totalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalOrders = salesData.stream()
                .mapToLong(SalesSummary::totalOrders)
                .sum();

        BigDecimal averageOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long orderConfirmedCount = eventStoreRepository.countByEventTypeAndTimestampRange("OrderConfirmed", from, to);
        long cartAbandonedCount = eventStoreRepository.countByEventTypeAndTimestampRange("CartAbandoned", from, to);

        long totalConversionBase = orderConfirmedCount + cartAbandonedCount;
        BigDecimal conversionRate = totalConversionBase > 0
                ? BigDecimal.valueOf(orderConfirmedCount).divide(BigDecimal.valueOf(totalConversionBase), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal cartAbandonmentRate = totalConversionBase > 0
                ? BigDecimal.valueOf(cartAbandonedCount).divide(BigDecimal.valueOf(totalConversionBase), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<TopSellingProduct> topSelling = salesSummaryRepository.findTopSellingByWeekRange(startDate, endDate, 10)
                .stream()
                .map(s -> TopSellingProduct.builder()
                        .sku(s.sku())
                        .productName(s.productName())
                        .totalQuantity(s.totalQuantity())
                        .totalRevenue(s.totalRevenue())
                        .build())
                .toList();

        return KpiResult.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .averageOrderValue(averageOrderValue)
                .conversionRate(conversionRate)
                .cartAbandonmentRate(cartAbandonmentRate)
                .averageDeliveryTimeDays(BigDecimal.ZERO) // Requires shipping event correlation
                .topSellingProducts(topSelling)
                .build();
    }
}

