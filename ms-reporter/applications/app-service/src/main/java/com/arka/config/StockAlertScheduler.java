package com.arka.config;

import com.arka.model.alert.StockAlert;
import com.arka.model.eventstore.DomainEventEnvelope;
import com.arka.model.eventstore.gateways.EventPublisherGateway;
import com.arka.usecase.stockalert.StockAlertUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockAlertScheduler {

    private final StockAlertUseCase stockAlertUseCase;
    private final EventPublisherGateway eventPublisherGateway;

    @Scheduled(cron = "${scheduler.stock-alert.cron}")
    public void runStockAlertAnalysis() {
        log.info("Starting scheduled stock alert analysis");
        try {
            List<StockAlert> alerts = stockAlertUseCase.analyzeStockPatterns();
            for (StockAlert alert : alerts) {
                publishAlertEvent(alert);
            }
            log.info("Stock alert analysis completed: {} alerts generated", alerts.size());
        } catch (Exception e) {
            log.error("Stock alert analysis failed", e);
        }
    }

    private void publishAlertEvent(StockAlert alert) {
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("StockAlertGenerated")
                .correlationId(alert.id().toString())
                .payload(Map.of(
                        "sku", alert.sku(),
                        "currentStock", alert.currentStock(),
                        "dailyRate", alert.dailyRate(),
                        "daysUntilOut", alert.daysUntilOut()
                ))
                .build();

        eventPublisherGateway.publish(envelope, alert.sku());
    }
}
