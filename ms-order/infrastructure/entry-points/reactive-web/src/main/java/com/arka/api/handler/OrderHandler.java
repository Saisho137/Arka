package com.arka.api.handler;

import com.arka.api.dto.request.CancelOrderRequest;
import com.arka.api.dto.request.ChangeStatusRequest;
import com.arka.api.dto.request.CreateOrderRequest;
import com.arka.api.dto.response.OrderResponse;
import com.arka.api.dto.response.OrderSummaryResponse;
import com.arka.api.mapper.OrderMapper;
import com.arka.usecase.order.OrderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderHandler {

    private final OrderUseCase orderUseCase;

    public Mono<ResponseEntity<OrderResponse>> createOrder(
            CreateOrderRequest request, UUID requesterId) {
        return orderUseCase.createOrder(
                        request.customerId(),
                        request.customerEmail(),
                        request.shippingAddress(),
                        request.notes(),
                        OrderMapper.toOrderItemCommands(request.items()))
                .flatMap(order -> orderUseCase.getOrder(order.id(), requesterId, true))
                .map(OrderMapper::toOrderResponse)
                .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response));
    }

    public Mono<ResponseEntity<OrderResponse>> getOrder(
            UUID orderId, UUID requesterId, boolean isAdmin) {
        return orderUseCase.getOrder(orderId, requesterId, isAdmin)
                .map(OrderMapper::toOrderResponse)
                .map(ResponseEntity::ok);
    }

    public Flux<OrderSummaryResponse> listOrders(
            String statusFilter, UUID requesterId, boolean isAdmin, int page, int size) {
        return orderUseCase.listOrders(statusFilter, requesterId, isAdmin, page, size)
                .map(OrderMapper::toOrderSummaryResponse);
    }

    public Mono<ResponseEntity<OrderResponse>> changeOrderStatus(
            UUID orderId, ChangeStatusRequest request, UUID adminId) {
        return orderUseCase.changeOrderStatus(orderId, request.newStatus(), adminId)
                .flatMap(order -> orderUseCase.getOrder(orderId, adminId, true))
                .map(OrderMapper::toOrderResponse)
                .map(ResponseEntity::ok);
    }

    public Mono<ResponseEntity<OrderResponse>> cancelOrder(
            UUID orderId, CancelOrderRequest request, UUID requesterId, boolean isAdmin) {
        return orderUseCase.cancelOrder(orderId, request.reason(), requesterId, isAdmin)
                .flatMap(order -> orderUseCase.getOrder(orderId, requesterId, isAdmin))
                .map(OrderMapper::toOrderResponse)
                .map(ResponseEntity::ok);
    }
}
