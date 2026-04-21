package com.arka.usecase.order;

import com.arka.model.commons.exception.AccessDeniedException;
import com.arka.model.commons.exception.CatalogServiceUnavailableException;
import com.arka.model.commons.exception.InsufficientStockException;
import com.arka.model.commons.exception.InvalidStateTransitionException;
import com.arka.model.commons.exception.InventoryServiceUnavailableException;
import com.arka.model.commons.exception.OrderNotFoundException;
import com.arka.model.commons.gateways.TransactionalGateway;
import com.arka.model.order.Order;
import com.arka.model.order.OrderItem;
import com.arka.model.order.OrderStateHistory;
import com.arka.model.order.OrderStateTransition;
import com.arka.model.order.OrderStatus;
import com.arka.model.order.ProductInfo;
import com.arka.model.order.ReserveStockResult;
import com.arka.model.order.gateways.CatalogClient;
import com.arka.model.order.gateways.InventoryClient;
import com.arka.model.order.gateways.OrderItemRepository;
import com.arka.model.order.gateways.OrderRepository;
import com.arka.model.order.gateways.OrderStateHistoryRepository;
import com.arka.model.outboxevent.DomainEventEnvelope;
import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OrderCancelledPayload;
import com.arka.model.outboxevent.OrderConfirmedPayload;
import com.arka.model.outboxevent.OrderItemPayload;
import com.arka.model.outboxevent.OrderStatusChangedPayload;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class OrderUseCase {

    private static final String ORDER_NOT_FOUND = "Order not found: ";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStateHistoryRepository orderStateHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final InventoryClient inventoryClient;
    private final CatalogClient catalogClient;
    private final JsonSerializer jsonSerializer;
    private final TransactionalGateway transactionalGateway;

    // ──────────────────────────────────────────────────────────────────
    // 1. CREATE ORDER — Phase 1 Saga (Catalog → Inventory → Confirm)
    // ──────────────────────────────────────────────────────────────────

    public Mono<Order> createOrder(UUID customerId, String customerEmail,
                                   String shippingAddress, String notes,
                                   List<OrderItemRequest> requestedItems) {

        UUID orderId = UUID.randomUUID();

        // Single pipeline per item: catalog(sku) → inventory(reserve) → ItemContext
        // concatMap ensures sequential gRPC calls, preserving order
        // collectList justified: saga requires ALL results to decide (fail-fast accumulation)
        return Flux.fromIterable(requestedItems)
                .concatMap(req -> catalogClient.getProductInfo(req.sku())
                        .onErrorMap(ex -> !(ex instanceof CatalogServiceUnavailableException),
                                ex -> new CatalogServiceUnavailableException(
                                        "Failed to fetch product info for SKU: " + req.sku()
                                                + ". Cause: " + ex.getMessage()))
                        .flatMap(info -> inventoryClient.reserveStock(req.sku(), orderId, req.quantity())
                                .onErrorMap(ex -> !(ex instanceof InventoryServiceUnavailableException),
                                        ex -> new InventoryServiceUnavailableException(
                                                "Failed to reserve stock for SKU: " + req.sku()
                                                        + ". Cause: " + ex.getMessage()))
                                .map(result -> new ItemContext(req, info, result))))
                .collectList()
                .flatMap(contexts -> {
                    List<String> failedSkus = contexts.stream()
                            .filter(ctx -> !ctx.reserveResult().success())
                            .map(ctx -> ctx.request().sku())
                            .toList();

                    if (!failedSkus.isEmpty()) {
                        return Mono.error(new InsufficientStockException(failedSkus));
                    }

                    return persistConfirmedOrder(customerId, customerEmail,
                            shippingAddress, notes, contexts);
                });
    }

    private Mono<Order> persistConfirmedOrder(UUID customerId, String customerEmail,
                                              String shippingAddress, String notes,
                                              List<ItemContext> contexts) {
        List<OrderItem> orderItems = contexts.stream()
                .map(ctx -> OrderItem.builder()
                        .productId(ctx.request().productId())
                        .sku(ctx.request().sku())
                        .productName(ctx.info().productName())
                        .quantity(ctx.request().quantity())
                        .unitPrice(ctx.info().unitPrice())
                        .build())
                .toList();

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .customerId(customerId)
                .customerEmail(customerEmail)
                .shippingAddress(shippingAddress)
                .notes(notes)
                .status(new OrderStatus.Confirmed())
                .totalAmount(totalAmount)
                .build();

        Mono<Order> pipeline = orderRepository.save(order)
                .flatMap(savedOrder -> {
                    List<OrderItem> itemsWithOrderId = orderItems.stream()
                            .map(item -> item.toBuilder().orderId(savedOrder.id()).build())
                            .toList();

                    OrderStateHistory history = OrderStateHistory.builder()
                            .orderId(savedOrder.id())
                            .previousStatus(new OrderStatus.PendingReserve().value())
                            .newStatus(new OrderStatus.Confirmed().value())
                            .changedBy(customerId)
                            .reason("Order created and stock reserved successfully")
                            .build();

                    OutboxEvent outboxEvent = buildOutboxEvent(
                            EventType.ORDER_CONFIRMED,
                            savedOrder.id().toString(),
                            OrderConfirmedPayload.builder()
                                    .orderId(savedOrder.id())
                                    .customerId(customerId)
                                    .customerEmail(customerEmail)
                                    .items(toItemPayloads(itemsWithOrderId))
                                    .totalAmount(totalAmount)
                                    .build());

                    return orderItemRepository.saveAll(itemsWithOrderId)
                            .then(orderStateHistoryRepository.save(history))
                            .then(outboxEventRepository.save(outboxEvent))
                            .thenReturn(savedOrder);
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // ──────────────────────────────────────────────────────────────────
    // 2. GET ORDER — By ID with items, access control
    // ──────────────────────────────────────────────────────────────────

    public Mono<OrderWithItems> getOrder(UUID orderId, UUID requesterId, boolean isAdmin) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(
                        ORDER_NOT_FOUND + orderId)))
                .flatMap(order -> {
                    if (!isAdmin && !order.customerId().equals(requesterId)) {
                        return Mono.error(new AccessDeniedException(
                                "Customer " + requesterId + " cannot access order " + orderId));
                    }
                    // collectList justified: composing domain aggregate (OrderWithItems),
                    // not streaming to client — entry-point decides streaming vs materialized
                    return orderItemRepository.findByOrderId(orderId)
                            .collectList()
                            .map(items -> new OrderWithItems(order, items));
                });
    }

    // ──────────────────────────────────────────────────────────────────
    // 3. LIST ORDERS — Paginated, filtered, access-controlled
    // ──────────────────────────────────────────────────────────────────

    public Flux<Order> listOrders(String statusFilter, UUID requesterId,
                                  boolean isAdmin, int page, int size) {
        OrderStatus status = parseStatusFilter(statusFilter);
        UUID customerId = isAdmin ? null : requesterId;
        return orderRepository.findByFilters(status, customerId, page, size);
    }

    public Mono<Long> countOrders(String statusFilter, UUID requesterId, boolean isAdmin) {
        OrderStatus status = parseStatusFilter(statusFilter);
        UUID customerId = isAdmin ? null : requesterId;
        return orderRepository.countByFilters(status, customerId);
    }

    private OrderStatus parseStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        return OrderStatus.fromValue(statusFilter);
    }

    // ──────────────────────────────────────────────────────────────────
    // 4. CHANGE ORDER STATUS — Admin-only, state machine validation
    // ──────────────────────────────────────────────────────────────────

    public Mono<Order> changeOrderStatus(UUID orderId, String newStatusValue, UUID adminId) {
        OrderStatus newStatus = OrderStatus.fromValue(newStatusValue);

        Mono<Order> pipeline = orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(
                        ORDER_NOT_FOUND + orderId)))
                .flatMap(order -> {
                    if (!OrderStateTransition.isValidTransition(order.status(), newStatus)) {
                        return Mono.error(new InvalidStateTransitionException(
                                "Cannot transition from " + order.status().value()
                                        + " to " + newStatus.value()));
                    }

                    OrderStateHistory history = OrderStateHistory.builder()
                            .orderId(orderId)
                            .previousStatus(order.status().value())
                            .newStatus(newStatus.value())
                            .changedBy(adminId)
                            .build();

                    OutboxEvent outboxEvent = buildOutboxEvent(
                            EventType.ORDER_STATUS_CHANGED,
                            orderId.toString(),
                            OrderStatusChangedPayload.builder()
                                    .orderId(orderId)
                                    .previousStatus(order.status().value())
                                    .newStatus(newStatus.value())
                                    .customerEmail(order.customerEmail())
                                    .build());

                    return orderRepository.updateStatus(orderId, newStatus)
                            .flatMap(updated -> orderStateHistoryRepository.save(history)
                                    .then(outboxEventRepository.save(outboxEvent))
                                    .thenReturn(updated));
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // ──────────────────────────────────────────────────────────────────
    // 5. CANCEL ORDER — State machine + access control
    // ──────────────────────────────────────────────────────────────────

    public Mono<Order> cancelOrder(UUID orderId, String reason,
                                   UUID requesterId, boolean isAdmin) {
        OrderStatus cancelled = new OrderStatus.Cancelled();

        Mono<Order> pipeline = orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new OrderNotFoundException(
                        ORDER_NOT_FOUND + orderId)))
                .flatMap(order -> {
                    if (!isAdmin && !order.customerId().equals(requesterId)) {
                        return Mono.error(new AccessDeniedException(
                                "Customer " + requesterId + " cannot cancel order " + orderId));
                    }
                    if (!OrderStateTransition.isValidTransition(order.status(), cancelled)) {
                        return Mono.error(new InvalidStateTransitionException(
                                "Cannot cancel order in status " + order.status().value()
                                        + ". Only CONFIRMADO orders can be cancelled."));
                    }

                    OrderStateHistory history = OrderStateHistory.builder()
                            .orderId(orderId)
                            .previousStatus(order.status().value())
                            .newStatus(cancelled.value())
                            .changedBy(requesterId)
                            .reason(reason)
                            .build();

                    OutboxEvent outboxEvent = buildOutboxEvent(
                            EventType.ORDER_CANCELLED,
                            orderId.toString(),
                            OrderCancelledPayload.builder()
                                    .orderId(orderId)
                                    .customerId(order.customerId())
                                    .customerEmail(order.customerEmail())
                                    .reason(reason)
                                    .build());

                    return orderRepository.updateStatus(orderId, cancelled)
                            .flatMap(updated -> orderStateHistoryRepository.save(history)
                                    .then(outboxEventRepository.save(outboxEvent))
                                    .thenReturn(updated));
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // ──────────────────────────────────────────────────────────────────
    // 6. PROCESS EXTERNAL EVENT — Idempotent consumer (Phase 2+)
    //    Handles: PaymentProcessed, PaymentFailed, ShippingDispatched
    //    Phase 1: deduplication only — routing logic added in Phase 2
    // ──────────────────────────────────────────────────────────────────

    public Mono<Void> processExternalEvent(UUID eventId) {
        Mono<Void> pipeline = processedEventRepository.exists(eventId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        return Mono.<Void>empty();
                    }
                    return processedEventRepository.save(eventId);
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // ──────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────

    private OutboxEvent buildOutboxEvent(EventType eventType, String partitionKey, Object payload) {
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType.value())
                .correlationId(partitionKey)
                .payload(payload)
                .build();

        return OutboxEvent.builder()
                .eventType(eventType)
                .payload(jsonSerializer.serialize(envelope))
                .partitionKey(partitionKey)
                .build();
    }

    private List<OrderItemPayload> toItemPayloads(List<OrderItem> items) {
        return items.stream()
                .map(item -> OrderItemPayload.builder()
                        .sku(item.sku())
                        .quantity(item.quantity())
                        .unitPrice(item.unitPrice())
                        .build())
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────
    // Inner records
    // ──────────────────────────────────────────────────────────────────

    public record OrderItemRequest(UUID productId, String sku, int quantity) {
    }

    public record OrderWithItems(Order order, List<OrderItem> items) {
    }

    private record ItemContext(
            OrderItemRequest request,
            ProductInfo info,
            ReserveStockResult reserveResult
    ) {
    }
}
