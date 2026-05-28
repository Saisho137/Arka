package com.arka.usecase.listsuppliers;

import com.arka.model.commons.PageResponse;
import com.arka.model.gateways.SupplierRepository;
import com.arka.model.supplier.Supplier;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ListSuppliersUseCase {

    private final SupplierRepository supplierRepository;

    public Mono<PageResponse<Supplier>> execute(int page, int size) {
        return supplierRepository.findAllActive(page, size)
                .collectList()
                .zipWith(supplierRepository.countActive())
                .map(tuple -> PageResponse.<Supplier>builder()
                        .content(tuple.getT1())
                        .page(page)
                        .size(size)
                        .totalElements(tuple.getT2())
                        .totalPages((int) Math.ceil((double) tuple.getT2() / size))
                        .build());
    }
}
