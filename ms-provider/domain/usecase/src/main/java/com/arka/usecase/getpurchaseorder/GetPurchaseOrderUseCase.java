package com.arka.usecase.getpurchaseorder;

import com.arka.model.commons.exception.PurchaseOrderNotFoundException;
import com.arka.model.gateways.PurchaseOrderRepository;
import com.arka.model.purchaseorder.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class GetPurchaseOrderUseCase {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public Mono<PurchaseOrder> execute(UUID purchaseOrderId) {
        return purchaseOrderRepository.findById(purchaseOrderId)
                .switchIfEmpty(Mono.error(new PurchaseOrderNotFoundException(purchaseOrderId.toString())))
                .flatMap(po -> purchaseOrderRepository.findItemsByPurchaseOrderId(po.id())
                        .collectList()
                        .map(items -> po.toBuilder().items(items).build()));
    }
}
