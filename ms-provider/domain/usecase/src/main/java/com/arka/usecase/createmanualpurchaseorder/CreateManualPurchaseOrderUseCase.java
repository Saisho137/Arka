package com.arka.usecase.createmanualpurchaseorder;

import com.arka.model.commons.exception.SupplierNotFoundException;
import com.arka.model.gateways.OutboxEventRepository;
import com.arka.model.gateways.PurchaseOrderRepository;
import com.arka.model.gateways.SupplierProductRepository;
import com.arka.model.gateways.SupplierRepository;
import com.arka.model.outbox.EventType;
import com.arka.model.outbox.OutboxEvent;
import com.arka.model.purchaseorder.PurchaseOrder;
import com.arka.model.purchaseorder.PurchaseOrderItem;
import com.arka.model.purchaseorder.PurchaseOrderStatus;
import com.arka.usecase.commons.JsonSerializer;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateManualPurchaseOrderUseCase {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonSerializer jsonSerializer;

    public Mono<PurchaseOrder> execute(UUID supplierId, List<ItemRequest> items, String notes) {
        return supplierRepository.findById(supplierId)
                .switchIfEmpty(Mono.error(new SupplierNotFoundException(supplierId.toString())))
                .flatMap(supplier -> createOrder(supplierId, items, notes));
    }

    private Mono<PurchaseOrder> createOrder(UUID supplierId, List<ItemRequest> items, String notes) {
        return Flux.fromIterable(items)
                .flatMap(item -> buildOrderItem(supplierId, item))
                .collectList()
                .flatMap(orderItems -> {
                    BigDecimal total = orderItems.stream()
                            .map(PurchaseOrderItem::subtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    PurchaseOrder po = PurchaseOrder.builder()
                            .supplierId(supplierId)
                            .status(PurchaseOrderStatus.PENDING)
                            .totalAmount(total)
                            .notes(notes)
                            .build();

                    return purchaseOrderRepository.save(po)
                            .flatMap(savedPo -> saveItems(savedPo, orderItems)
                                    .then(saveOutboxEvent(savedPo))
                                    .thenReturn(savedPo.toBuilder().items(orderItems).build()));
                });
    }

    private Mono<PurchaseOrderItem> buildOrderItem(UUID supplierId, ItemRequest item) {
        return supplierProductRepository.findBySupplierIdAndSku(supplierId, item.sku())
                .map(sp -> PurchaseOrderItem.builder()
                        .sku(item.sku())
                        .productName(item.sku())
                        .quantity(item.quantity())
                        .unitPrice(sp.unitPrice())
                        .subtotal(sp.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                        .build())
                .switchIfEmpty(Mono.just(PurchaseOrderItem.builder()
                        .sku(item.sku())
                        .productName(item.sku())
                        .quantity(item.quantity())
                        .unitPrice(BigDecimal.ZERO)
                        .subtotal(BigDecimal.ZERO)
                        .build()));
    }

    private Mono<Void> saveItems(PurchaseOrder po, List<PurchaseOrderItem> items) {
        return Flux.fromIterable(items)
                .flatMap(item -> purchaseOrderRepository.saveItem(
                        item.toBuilder().purchaseOrderId(po.id()).build()))
                .then();
    }

    private Mono<OutboxEvent> saveOutboxEvent(PurchaseOrder po) {
        String payload = buildPayloadJson(po);
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(EventType.PURCHASE_ORDER_CREATED)
                .partitionKey(po.id().toString())
                .payload(payload)
                .build();
        return outboxEventRepository.save(outboxEvent);
    }

    private String buildPayloadJson(PurchaseOrder po) {
        return jsonSerializer.serialize(Map.of(
                "purchaseOrderId", po.id().toString(),
                "supplierId", po.supplierId().toString(),
                "totalAmount", po.totalAmount().toPlainString(),
                "status", po.status().name()
        ));
    }

    public record ItemRequest(String sku, int quantity) {}
}
