package com.arka.usecase.eventconsumption;

import com.arka.model.eventstore.EventEnvelope;
import com.arka.model.eventstore.EventStoreEntry;
import com.arka.model.eventstore.gateways.EventStoreRepository;
import com.arka.model.eventstore.gateways.ProcessedEventRepository;
import com.arka.model.sales.SalesSummary;
import com.arka.model.sales.gateways.SalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class EventConsumptionUseCase {

    private final EventStoreRepository eventStoreRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final SalesSummaryRepository salesSummaryRepository;

    public void consume(EventEnvelope envelope) {
        UUID eventId = envelope.eventId();

        if (processedEventRepository.exists(eventId)) {
            log.debug("Event already processed, skipping: {}", eventId);
            return;
        }

        EventStoreEntry entry = EventStoreEntry.builder()
                .eventId(eventId)
                .eventType(envelope.eventType())
                .source(envelope.source())
                .aggregateId(extractAggregateId(envelope))
                .correlationId(envelope.correlationId())
                .payload(envelope.payload())
                .timestamp(envelope.timestamp())
                .build();

        eventStoreRepository.save(entry);
        projectToReadModels(envelope);
        processedEventRepository.save(eventId);
    }

    private void projectToReadModels(EventEnvelope envelope) {
        switch (envelope.eventType()) {
            case "OrderConfirmed" -> projectOrderConfirmed(envelope);
            case "OrderCancelled" -> projectOrderCancelled(envelope);
            case "StockUpdated" -> projectStockUpdated(envelope);
            case "PriceChanged" -> projectPriceChanged(envelope);
            default -> log.warn("Unknown event type, stored but not projected: {}", envelope.eventType());
        }
    }

    @SuppressWarnings("unchecked")
    private void projectOrderConfirmed(EventEnvelope envelope) {
        Map<String, Object> payload = (Map<String, Object>) envelope.payload();
        String sku = getStringFromPayload(payload, "sku");
        int quantity = getIntFromPayload(payload, "quantity", 1);
        BigDecimal amount = getBigDecimalFromPayload(payload, "totalAmount");
        String productName = getStringFromPayload(payload, "productName");
        LocalDate weekStart = getWeekStart(envelope.timestamp());

        SalesSummary summary = SalesSummary.builder()
                .sku(sku)
                .weekStartDate(weekStart)
                .totalOrders(1)
                .totalQuantity(quantity)
                .totalRevenue(amount)
                .productName(productName)
                .build();

        salesSummaryRepository.upsert(summary);
    }

    @SuppressWarnings("unchecked")
    private void projectOrderCancelled(EventEnvelope envelope) {
        Map<String, Object> payload = (Map<String, Object>) envelope.payload();
        String sku = getStringFromPayload(payload, "sku");
        int quantity = getIntFromPayload(payload, "quantity", 1);
        BigDecimal amount = getBigDecimalFromPayload(payload, "totalAmount");
        LocalDate weekStart = getWeekStart(envelope.timestamp());

        salesSummaryRepository.decrementSales(sku, weekStart, quantity, amount);
    }

    @SuppressWarnings("unchecked")
    private void projectStockUpdated(EventEnvelope envelope) {
        // Stock updates are stored in event_store for KPI calculations
        log.debug("StockUpdated event stored for analytics: {}", envelope.eventId());
    }

    @SuppressWarnings("unchecked")
    private void projectPriceChanged(EventEnvelope envelope) {
        // Price changes are stored in event_store for analytics
        log.debug("PriceChanged event stored for analytics: {}", envelope.eventId());
    }

    @SuppressWarnings("unchecked")
    private String extractAggregateId(EventEnvelope envelope) {
        Object payload = envelope.payload();
        if (payload instanceof Map<?, ?> map) {
            if (map.containsKey("orderId")) return String.valueOf(map.get("orderId"));
            if (map.containsKey("sku")) return String.valueOf(map.get("sku"));
            if (map.containsKey("productId")) return String.valueOf(map.get("productId"));
            if (map.containsKey("cartId")) return String.valueOf(map.get("cartId"));
        }
        return envelope.eventId().toString();
    }

    private LocalDate getWeekStart(Instant timestamp) {
        LocalDate date = timestamp.atOffset(ZoneOffset.UTC).toLocalDate();
        return date.with(ChronoField.DAY_OF_WEEK, 1);
    }

    @SuppressWarnings("unchecked")
    private String getStringFromPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : "unknown";
    }

    private int getIntFromPayload(Map<String, Object> payload, String key, int defaultValue) {
        Object value = payload.get(key);
        if (value instanceof Number number) return number.intValue();
        return defaultValue;
    }

    private BigDecimal getBigDecimalFromPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }
}

