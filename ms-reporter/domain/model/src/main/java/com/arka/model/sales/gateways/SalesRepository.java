package com.arka.model.sales.gateways;

import com.arka.model.sales.SalesSummary;

import java.time.LocalDate;
import java.util.List;

public interface SalesSummaryRepository {

    void upsert(SalesSummary summary);

    void decrementSales(String sku, LocalDate weekStartDate, int quantity, java.math.BigDecimal amount);

    List<SalesSummary> findByWeekRange(LocalDate from, LocalDate to);

    List<SalesSummary> findTopSellingByWeekRange(LocalDate from, LocalDate to, int limit);

    void truncate();
}
