package com.arka.api.supplier;

import com.arka.api.dto.CreateSupplierProductRequest;
import com.arka.model.supplier.Supplier;
import com.arka.model.supplier.SupplierProduct;
import com.arka.usecase.assignsupplierproduct.AssignSupplierProductUseCase;
import com.arka.usecase.createsupplier.CreateSupplierUseCase;
import com.arka.usecase.deactivatesupplier.DeactivateSupplierUseCase;
import com.arka.usecase.getsupplier.GetSupplierUseCase;
import com.arka.usecase.listsuppliers.ListSuppliersUseCase;
import com.arka.usecase.updatesupplier.UpdateSupplierUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SupplierHandler {

    private final CreateSupplierUseCase createSupplierUseCase;
    private final GetSupplierUseCase getSupplierUseCase;
    private final ListSuppliersUseCase listSuppliersUseCase;
    private final UpdateSupplierUseCase updateSupplierUseCase;
    private final DeactivateSupplierUseCase deactivateSupplierUseCase;
    private final AssignSupplierProductUseCase assignSupplierProductUseCase;

    public Mono<ServerResponse> createSupplier(ServerRequest request) {
        return request.bodyToMono(Supplier.class)
                .flatMap(createSupplierUseCase::execute)
                .flatMap(supplier -> ServerResponse.status(HttpStatus.CREATED).bodyValue(supplier));
    }

    public Mono<ServerResponse> listSuppliers(ServerRequest request) {
        int page = Integer.parseInt(request.queryParam("page").orElse("0"));
        int size = Integer.parseInt(request.queryParam("size").orElse("20"));
        return listSuppliersUseCase.execute(page, size)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    public Mono<ServerResponse> getSupplier(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("supplierId"));
        return getSupplierUseCase.execute(id)
                .flatMap(supplier -> ServerResponse.ok().bodyValue(supplier));
    }

    public Mono<ServerResponse> updateSupplier(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("supplierId"));
        return request.bodyToMono(Supplier.class)
                .flatMap(data -> updateSupplierUseCase.execute(id, data))
                .flatMap(supplier -> ServerResponse.ok().bodyValue(supplier));
    }

    public Mono<ServerResponse> deactivateSupplier(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("supplierId"));
        return deactivateSupplierUseCase.execute(id)
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> assignProduct(ServerRequest request) {
        UUID supplierId = UUID.fromString(request.pathVariable("supplierId"));
        return request.bodyToMono(CreateSupplierProductRequest.class)
                .map(req -> SupplierProduct.builder()
                        .supplierId(supplierId)
                        .sku(req.sku())
                        .supplierSku(req.supplierSku())
                        .unitPrice(req.unitPrice())
                        .leadTimeDays(req.leadTimeDays())
                        .reorderMultiplier(req.reorderMultiplier())
                        .preferred(req.preferred())
                        .build())
                .flatMap(product -> assignSupplierProductUseCase.execute(supplierId, product))
                .flatMap(product -> ServerResponse.status(HttpStatus.CREATED).bodyValue(product));
    }

    public Mono<ServerResponse> removeProduct(ServerRequest request) {
        UUID supplierId = UUID.fromString(request.pathVariable("supplierId"));
        String sku = request.pathVariable("sku");
        return assignSupplierProductUseCase.remove(supplierId, sku)
                .then(ServerResponse.noContent().build());
    }
}
