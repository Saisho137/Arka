package com.arka.api.purchaseorder;

import com.arka.api.dto.CreatePurchaseOrderRequest;
import com.arka.model.purchaseorder.PurchaseOrderStatus;
import com.arka.usecase.createmanualpurchaseorder.CreateManualPurchaseOrderUseCase;
import com.arka.usecase.getpurchaseorder.GetPurchaseOrderUseCase;
import com.arka.usecase.listpurchaseorders.ListPurchaseOrdersUseCase;
import com.arka.usecase.transitionpurchaseorder.TransitionPurchaseOrderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PurchaseOrderHandler {

    private final ListPurchaseOrdersUseCase listPurchaseOrdersUseCase;
    private final GetPurchaseOrderUseCase getPurchaseOrderUseCase;
    private final TransitionPurchaseOrderUseCase transitionPurchaseOrderUseCase;
    private final CreateManualPurchaseOrderUseCase createManualPurchaseOrderUseCase;

    public Mono<ServerResponse> listPurchaseOrders(ServerRequest request) {
        int page = Integer.parseInt(request.queryParam("page").orElse("0"));
        int size = Integer.parseInt(request.queryParam("size").orElse("20"));

        PurchaseOrderStatus status = request.queryParam("status")
                .map(PurchaseOrderStatus::valueOf)
                .orElse(null);
        UUID supplierId = request.queryParam("supplierId")
                .map(UUID::fromString)
                .orElse(null);
        String sku = request.queryParam("sku").orElse(null);
        Instant dateFrom = request.queryParam("dateFrom")
                .map(Instant::parse)
                .orElse(null);
        Instant dateTo = request.queryParam("dateTo")
                .map(Instant::parse)
                .orElse(null);

        return listPurchaseOrdersUseCase.execute(status, supplierId, sku, dateFrom, dateTo, page, size)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    public Mono<ServerResponse> getPurchaseOrder(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("id"));
        return getPurchaseOrderUseCase.execute(id)
                .flatMap(po -> ServerResponse.ok().bodyValue(po));
    }

    public Mono<ServerResponse> createPurchaseOrder(ServerRequest request) {
        return request.bodyToMono(CreatePurchaseOrderRequest.class)
                .flatMap(req -> {
                    UUID supplierId = UUID.fromString(req.supplierId());
                    var items = req.items().stream()
                            .map(i -> new CreateManualPurchaseOrderUseCase.ItemRequest(i.sku(), i.quantity()))
                            .toList();
                    return createManualPurchaseOrderUseCase.execute(supplierId, items, req.notes());
                })
                .flatMap(po -> ServerResponse.status(HttpStatus.CREATED).bodyValue(po));
    }

    public Mono<ServerResponse> sendPurchaseOrder(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("id"));
        return transitionPurchaseOrderUseCase.execute(id, PurchaseOrderStatus.SENT)
                .flatMap(po -> ServerResponse.ok().bodyValue(po));
    }

    public Mono<ServerResponse> confirmPurchaseOrder(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("id"));
        return transitionPurchaseOrderUseCase.execute(id, PurchaseOrderStatus.CONFIRMED)
                .flatMap(po -> ServerResponse.ok().bodyValue(po));
    }

    public Mono<ServerResponse> receivePurchaseOrder(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("id"));
        return transitionPurchaseOrderUseCase.execute(id, PurchaseOrderStatus.RECEIVED)
                .flatMap(po -> ServerResponse.ok().bodyValue(po));
    }

    public Mono<ServerResponse> cancelPurchaseOrder(ServerRequest request) {
        UUID id = UUID.fromString(request.pathVariable("id"));
        return transitionPurchaseOrderUseCase.execute(id, PurchaseOrderStatus.CANCELLED)
                .flatMap(po -> ServerResponse.ok().bodyValue(po));
    }
}
