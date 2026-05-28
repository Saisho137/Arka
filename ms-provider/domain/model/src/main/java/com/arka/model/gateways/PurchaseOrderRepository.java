package com.arka.model.gateways;

import com.arka.model.purchaseorder.PurchaseOrder;
import com.arka.model.purchaseorder.PurchaseOrderItem;
import com.arka.model.purchaseorder.PurchaseOrderStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PurchaseOrderRepository {

    Mono<PurchaseOrder> save(PurchaseOrder purchaseOrder);

    Mono<PurchaseOrderItem> saveItem(PurchaseOrderItem item);

    Mono<PurchaseOrder> findById(UUID id);

    Flux<PurchaseOrderItem> findItemsByPurchaseOrderId(UUID purchaseOrderId);

    Flux<PurchaseOrder> findAll(PurchaseOrderStatus status, UUID supplierId, String sku,
                                Instant dateFrom, Instant dateTo, int page, int size);

    Mono<Long> count(PurchaseOrderStatus status, UUID supplierId, String sku,
                     Instant dateFrom, Instant dateTo);

    Mono<Boolean> existsBySkuAndStatusIn(String sku, List<PurchaseOrderStatus> statuses);

    Mono<PurchaseOrder> updateStatus(PurchaseOrder purchaseOrder);
}
