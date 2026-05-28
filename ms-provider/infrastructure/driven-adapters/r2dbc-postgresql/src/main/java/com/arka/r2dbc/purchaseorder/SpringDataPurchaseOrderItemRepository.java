package com.arka.r2dbc.purchaseorder;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SpringDataPurchaseOrderItemRepository extends ReactiveCrudRepository<PurchaseOrderItemEntity, UUID> {

    Flux<PurchaseOrderItemEntity> findByPurchaseOrderId(UUID purchaseOrderId);
}
