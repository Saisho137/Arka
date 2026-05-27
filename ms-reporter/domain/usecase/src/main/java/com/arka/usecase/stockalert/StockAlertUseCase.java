package com.arka.usecase.stockalert;

import com.arka.model.alert.StockAlert;
import com.arka.model.alert.gateways.StockAlertRepository;
import com.arka.model.eventstore.EventStoreEntry;
import com.arka.model.eventstore.gateways.EventStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class StockAlertUseCase {

    private static final int ALERT_THRESHOLD_DAYS = 7;
    private static final int ANALYSIS_WINDOW_DAYS = 30;

    private final EventStoreRepository eventStoreRepository;
    private final StockAlertRepository stockAlertRepository;

    public List<StockAlert> analyzeStockPatterns() {
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(ANALYSIS_WINDOW_DAYS, ChronoUnit.DAYS);

        List<EventStoreEntry> orderEvents = eventStoreRepository
                .findByEventTypeAndTimestampRange("OrderConfirmed", thirtyDaysAgo, now);

        // Group by SKU and calculate daily consumption rate
        Map<String, List<EventStoreEntry>> eventsBySku = orderEvents.stream()
                .collect(Collectors.groupingBy(e -> extractSku(e.payload())));

        List<EventStoreEntry> stockEvents = eventStoreRepository
                .findByEventTypeAndTimestampRange("StockUpdated", thirtyDaysAgo, now);

        Map<String, Integer> currentStockBySku = stockEvents.stream()
                .collect(Collectors.toMap(
                        e -> extractSku(e.payload()),
                        e -> extractStock(e.payload()),
                        (a, b) -> b // Keep latest
                ));

        List<StockAlert> generatedAlerts = eventsBySku.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= ALERT_THRESHOLD_DAYS)
                .map(entry -> {
                    String sku = entry.getKey();
                    int totalQuantity = entry.getValue().stream()
                            .mapToInt(e -> extractQuantity(e.payload()))
                            .sum();

                    BigDecimal dailyRate = BigDecimal.valueOf(totalQuantity)
                            .divide(BigDecimal.valueOf(ANALYSIS_WINDOW_DAYS), 2, RoundingMode.HALF_UP);

                    int currentStock = currentStockBySku.getOrDefault(sku, 0);
                    int daysUntilOut = dailyRate.compareTo(BigDecimal.ZERO) > 0
                            ? BigDecimal.valueOf(currentStock).divide(dailyRate, 0, RoundingMode.FLOOR).intValue()
                            : Integer.MAX_VALUE;

                    return Map.entry(sku, StockAlert.builder()
                            .sku(sku)
                            .currentStock(currentStock)
                            .dailyRate(dailyRate)
                            .daysUntilOut(daysUntilOut)
                            .build());
                })
                .filter(entry -> entry.getValue().daysUntilOut() <= ALERT_THRESHOLD_DAYS)
                .map(entry -> {
                    StockAlert alert = entry.getValue();
                    // Check if active alert already exists
                    if (stockAlertRepository.findActiveAlertBySku(alert.sku()).isEmpty()) {
                        stockAlertRepository.save(alert);
                        log.info("Stock alert generated: sku={}, daysUntilOut={}", alert.sku(), alert.daysUntilOut());
                    }
                    return alert;
                })
                .toList();

        // Resolve alerts for SKUs that are no longer at risk
        resolveRecoveredAlerts(currentStockBySku, eventsBySku);

        return generatedAlerts;
    }

    private void resolveRecoveredAlerts(Map<String, Integer> currentStockBySku, Map<String, List<EventStoreEntry>> eventsBySku) {
        currentStockBySku.forEach((sku, stock) -> {
            List<EventStoreEntry> events = eventsBySku.get(sku);
            if (events == null || events.isEmpty()) return;

            int totalQuantity = events.stream().mapToInt(e -> extractQuantity(e.payload())).sum();
            BigDecimal dailyRate = BigDecimal.valueOf(totalQuantity)
                    .divide(BigDecimal.valueOf(ANALYSIS_WINDOW_DAYS), 2, RoundingMode.HALF_UP);

            BigDecimal threshold = dailyRate.multiply(BigDecimal.valueOf(ALERT_THRESHOLD_DAYS));
            if (BigDecimal.valueOf(stock).compareTo(threshold.add(threshold)) > 0) {
                stockAlertRepository.findActiveAlertBySku(sku).ifPresent(alert -> {
                    stockAlertRepository.resolveAlert(sku);
                    log.info("Stock alert resolved: sku={}", sku);
                });
            }
        });
    }

    @SuppressWarnings("unchecked")
    private String extractSku(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object sku = map.get("sku");
            return sku != null ? sku.toString() : "unknown";
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private int extractQuantity(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object qty = map.get("quantity");
            if (qty instanceof Number n) return n.intValue();
        }
        return 1;
    }

    @SuppressWarnings("unchecked")
    private int extractStock(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object stock = map.get("currentStock");
            if (stock instanceof Number n) return n.intValue();
        }
        return 0;
    }
}

