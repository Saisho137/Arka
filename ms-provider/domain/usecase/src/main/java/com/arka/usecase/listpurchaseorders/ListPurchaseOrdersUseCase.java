package com.arka.usecase.listpurchaseorders;

import com.arka.model.commons.PageResponse;
import com.arka.model.gateways.PurchaseOrderRepository;
import com.arka.model.purchaseorder.PurchaseOrder;
import com.arka.model.purchaseorder.PurchaseOrderStatus;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ListPurchaseOrdersUseCase {

    private final PurchaseOrderRepository purchaseOrderRepository;

    public Mono<PageResponse<PurchaseOrder>> execute(PurchaseOrderStatus status, UUID supplierId,
                                                     String sku, Instant dateFrom, Instant dateTo,
                                                     int page, int size) {
        return purchaseOrderRepository.findAll(status, supplierId, sku, dateFrom, dateTo, page, size)
                .collectList()
                .zipWith(purchaseOrderRepository.count(status, supplierId, sku, dateFrom, dateTo))
                .map(tuple -> PageResponse.<PurchaseOrder>builder()
                        .content(tuple.getT1())
                        .page(page)
                        .size(size)
                        .totalElements(tuple.getT2())
                        .totalPages((int) Math.ceil((double) tuple.getT2() / size))
                        .build());
    }
}
