package com.arka.grpc.catalog;

import com.arka.grpc.CatalogServiceGrpc;
import com.arka.grpc.GetProductInfoRequest;
import com.arka.grpc.GetProductInfoResponse;
import com.arka.model.commons.exception.CatalogServiceUnavailableException;
import com.arka.model.order.ProductInfo;
import com.arka.model.order.gateways.CatalogClient;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class GrpcCatalogClient implements CatalogClient {

    private final CatalogServiceGrpc.CatalogServiceStub catalogStub;

    public GrpcCatalogClient(@GrpcClient("ms-catalog") CatalogServiceGrpc.CatalogServiceStub catalogStub) {
        this.catalogStub = catalogStub;
    }

    @Override
    public Mono<ProductInfo> getProductInfo(String sku) {
        return Mono.create(sink -> {
            GetProductInfoRequest request = GetProductInfoRequest.newBuilder()
                    .setSku(sku)
                    .build();

            catalogStub.getProductInfo(request, new StreamObserver<>() {
                @Override
                public void onNext(GetProductInfoResponse response) {
                    try {
                        BigDecimal unitPrice = new BigDecimal(response.getUnitPrice());
                        UUID productId = UUID.fromString(response.getProductId());
                        sink.success(ProductInfo.builder()
                                .productId(productId)
                                .sku(response.getSku())
                                .productName(response.getProductName())
                                .unitPrice(unitPrice)
                                .build());
                    } catch (NumberFormatException e) {
                        log.error("Invalid unitPrice format for sku={}: {}", sku, response.getUnitPrice());
                        sink.error(new CatalogServiceUnavailableException(
                                "Invalid price format received from catalog for sku: " + sku));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("gRPC getProductInfo error for sku={}: {}", sku, t.getMessage());
                    if (t instanceof StatusRuntimeException sre) {
                        Status.Code code = sre.getStatus().getCode();
                        if (code == Status.Code.NOT_FOUND) {
                            sink.error(new CatalogServiceUnavailableException(
                                    "Product not found in catalog for sku: " + sku));
                            return;
                        }
                        if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                            sink.error(new CatalogServiceUnavailableException(
                                    "Catalog service unavailable: " + sre.getStatus().getDescription()));
                            return;
                        }
                    }
                    sink.error(new CatalogServiceUnavailableException(
                            "Unexpected error communicating with catalog service: " + t.getMessage()));
                }

                @Override
                public void onCompleted() {
                    // handled in onNext
                }
            });
        });
    }
}
