package com.arka.usecase.generatepurchaseorder;

import com.arka.model.gateways.OutboxEventRepository;
import com.arka.model.gateways.ProcessedEventRepository;
import com.arka.model.gateways.PurchaseOrderRepository;
import com.arka.model.gateways.SupplierProductRepository;
import com.arka.model.outbox.EventType;
import com.arka.model.outbox.OutboxEvent;
import com.arka.model.purchaseorder.PurchaseOrder;
import com.arka.model.purchaseorder.PurchaseOrderItem;
import com.arka.model.purchaseorder.PurchaseOrderStatus;
import com.arka.model.supplier.SupplierProduct;
import com.arka.usecase.commons.JsonSerializer;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class GeneratePurchaseOrderUseCase {

    private static final System.Logger log = System.getLogger(GeneratePurchaseOrderUseCase.class.getName());

    private final ProcessedEventRepository processedEventRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonSerializer jsonSerializer;

    public Mono<Void> execute(UUID eventId, String sku, int currentStock, int threshold) {
        return processedEventRepository.exists(eventId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        log.log(System.Logger.Level.INFO, "Event {0} already processed — skipping", eventId);
                        return Mono.empty();
                    }
                    return checkAndCreateOrder(eventId, sku, currentStock, threshold);
                });
    }

    private Mono<Void> checkAndCreateOrder(UUID eventId, String sku, int currentStock, int threshold) {
        return purchaseOrderRepository.existsBySkuAndStatusIn(sku,
                        List.of(PurchaseOrderStatus.PENDING, PurchaseOrderStatus.SENT))
                .flatMap(activeOrderExists -> {
                    if (Boolean.TRUE.equals(activeOrderExists)) {
                        log.log(System.Logger.Level.INFO, "Active PO already exists for SKU {0} — skipping", sku);
                        return processedEventRepository.save(eventId);
                    }
                    return findSupplierAndCreateOrder(eventId, sku, currentStock, threshold);
                });
    }

    private Mono<Void> findSupplierAndCreateOrder(UUID eventId, String sku, int currentStock, int threshold) {
        return supplierProductRepository.findPreferredBySku(sku)
                .flatMap(supplierProduct -> createPurchaseOrder(eventId, sku, currentStock, threshold, supplierProduct))
                .switchIfEmpty(Mono.defer(() -> {
                    log.log(System.Logger.Level.WARNING, "No preferred supplier found for SKU {0} — cannot auto-generate PO", sku);
                    return processedEventRepository.save(eventId);
                }));
    }

    private Mono<Void> createPurchaseOrder(UUID eventId, String sku, int currentStock,
                                            int threshold, SupplierProduct supplierProduct) {
        int reorderQuantity = Math.max(1,
                supplierProduct.reorderMultiplier().multiply(BigDecimal.valueOf(threshold)).intValue() - currentStock);

        BigDecimal subtotal = supplierProduct.unitPrice().multiply(BigDecimal.valueOf(reorderQuantity));

        PurchaseOrder purchaseOrder = PurchaseOrder.builder()
                .supplierId(supplierProduct.supplierId())
                .status(PurchaseOrderStatus.PENDING)
                .totalAmount(subtotal)
                .notes("Auto-generated from StockDepleted event " + eventId)
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .sku(sku)
                .productName(sku)
                .quantity(reorderQuantity)
                .unitPrice(supplierProduct.unitPrice())
                .subtotal(subtotal)
                .build();

        return purchaseOrderRepository.save(purchaseOrder)
                .flatMap(savedPo -> {
                    PurchaseOrderItem itemWithPoId = item.toBuilder()
                            .purchaseOrderId(savedPo.id())
                            .build();
                    return purchaseOrderRepository.saveItem(itemWithPoId)
                            .thenReturn(savedPo);
                })
                .flatMap(savedPo -> saveOutboxEvent(savedPo, sku))
                .then(processedEventRepository.save(eventId))
                .doOnSuccess(v -> log.log(System.Logger.Level.INFO, "Created PO for SKU {0} from event {1}", sku, eventId));
    }

    private Mono<OutboxEvent> saveOutboxEvent(PurchaseOrder po, String sku) {
        String payload = buildPayloadJson(po, sku);
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(EventType.PURCHASE_ORDER_CREATED)
                .partitionKey(po.id().toString())
                .payload(payload)
                .build();
        return outboxEventRepository.save(outboxEvent);
    }

    private String buildPayloadJson(PurchaseOrder po, String sku) {
        return jsonSerializer.serialize(Map.of(
                "purchaseOrderId", po.id().toString(),
                "supplierId", po.supplierId().toString(),
                "sku", sku,
                "totalAmount", po.totalAmount().toPlainString(),
                "status", po.status().name()
        ));
    }
}
