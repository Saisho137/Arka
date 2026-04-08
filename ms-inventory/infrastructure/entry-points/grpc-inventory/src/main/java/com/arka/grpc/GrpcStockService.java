package com.arka.grpc;

import com.arka.model.commons.exception.InsufficientStockException;
import com.arka.model.commons.exception.StockNotFoundException;
import com.arka.usecase.stock.ReserveStockResult;
import com.arka.usecase.stock.StockUseCase;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcStockService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final StockUseCase stockUseCase;

    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        String sku = request.getSku();
        UUID orderId;

        try {
            orderId = UUID.fromString(request.getOrderId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid orderId format: " + request.getOrderId())
                    .asRuntimeException());
            return;
        }

        int quantity = request.getQuantity();

        stockUseCase.reserveStock(sku, orderId, quantity)
                .subscribe(
                        result -> {
                            responseObserver.onNext(toResponse(result));
                            responseObserver.onCompleted();
                        },
                        error -> {
                            log.error("gRPC ReserveStock error for sku={}, orderId={}: {}", sku, orderId, error.getMessage());
                            responseObserver.onError(toGrpcStatus(error).asRuntimeException());
                        }
                );
    }

    private ReserveStockResponse toResponse(ReserveStockResult result) {
        ReserveStockResponse.Builder builder = ReserveStockResponse.newBuilder()
                .setSuccess(result.success())
                .setAvailableQuantity(result.availableQuantity());

        if (result.reservationId() != null) {
            builder.setReservationId(result.reservationId().toString());
        }
        if (result.reason() != null) {
            builder.setReason(result.reason());
        }

        return builder.build();
    }

    private Status toGrpcStatus(Throwable error) {
        if (error instanceof StockNotFoundException) {
            return Status.NOT_FOUND.withDescription(error.getMessage());
        }
        if (error instanceof InsufficientStockException) {
            return Status.FAILED_PRECONDITION.withDescription(error.getMessage());
        }
        return Status.INTERNAL.withDescription("Internal server error");
    }
}
