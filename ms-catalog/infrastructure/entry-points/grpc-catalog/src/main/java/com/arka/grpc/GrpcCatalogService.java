package com.arka.grpc;

import com.arka.model.commons.exception.InvalidProductStateException;
import com.arka.model.commons.exception.ProductNotFoundException;
import com.arka.model.product.Product;
import com.arka.usecase.product.ProductUseCase;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcCatalogService extends CatalogServiceGrpc.CatalogServiceImplBase {

    private final ProductUseCase productUseCase;

    @Override
    public void getProductInfo(GetProductInfoRequest request, StreamObserver<GetProductInfoResponse> responseObserver) {
        String sku = request.getSku();

        if (sku == null || sku.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("SKU is required and cannot be blank")
                    .asRuntimeException());
            return;
        }

        productUseCase.getBySku(sku)
                .subscribe(
                        product -> {
                            responseObserver.onNext(toResponse(product));
                            responseObserver.onCompleted();
                        },
                        error -> {
                            log.error("gRPC GetProductInfo error for sku={}: {}", sku, error.getMessage());
                            responseObserver.onError(toGrpcStatus(error).asRuntimeException());
                        }
                );
    }

    private GetProductInfoResponse toResponse(Product product) {
        GetProductInfoResponse.Builder builder = GetProductInfoResponse.newBuilder()
                .setSku(product.sku())
                .setProductName(product.name())
                .setUnitPrice(product.price().amount().toPlainString())
                .setCurrency(product.price().currency())
                .setActive(product.active())
                .setProductId(product.id().toString());

        return builder.build();
    }

    private Status toGrpcStatus(Throwable error) {
        if (error instanceof ProductNotFoundException) {
            return Status.NOT_FOUND.withDescription(error.getMessage());
        }
        if (error instanceof InvalidProductStateException) {
            return Status.FAILED_PRECONDITION.withDescription(error.getMessage());
        }
        return Status.INTERNAL.withDescription("Internal server error");
    }
}
