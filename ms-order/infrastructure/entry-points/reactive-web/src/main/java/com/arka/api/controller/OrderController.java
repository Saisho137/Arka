package com.arka.api.controller;

import com.arka.api.dto.request.CancelOrderRequest;
import com.arka.api.dto.request.ChangeStatusRequest;
import com.arka.api.dto.request.CreateOrderRequest;
import com.arka.api.dto.response.OrderResponse;
import com.arka.api.dto.response.OrderSummaryResponse;
import com.arka.api.handler.OrderHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ROLE_ADMIN = "ADMIN";

    private final OrderHandler orderHandler;

    @PostMapping
    @Operation(summary = "Create a new order")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Order accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock"),
            @ApiResponse(responseCode = "503", description = "Inventory or Catalog service unavailable")
    })
    public Mono<ResponseEntity<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(description = "Authenticated user email (injected by API Gateway)", required = true)
            @RequestHeader("X-User-Email") String userEmail) {
        UUID requesterId = request.customerId();
        return orderHandler.createOrder(request, requesterId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public Mono<ResponseEntity<OrderResponse>> getOrder(
            @Parameter(description = "Order UUID") @PathVariable("id") UUID orderId,
            @Parameter(description = "Authenticated user email (injected by API Gateway)", required = true)
            @RequestHeader("X-User-Email") String userEmail,
            @Parameter(description = "User role: ADMIN or CUSTOMER (injected by API Gateway)", required = true)
            @RequestHeader("X-User-Role") String userRole) {
        UUID requesterId = extractUserIdFromEmail(userEmail);
        boolean isAdmin = ROLE_ADMIN.equals(userRole);
        return orderHandler.getOrder(orderId, requesterId, isAdmin);
    }

    @GetMapping
    @Operation(summary = "List orders with filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders returned"),
            @ApiResponse(responseCode = "400", description = "Invalid status filter")
    })
    public Flux<OrderSummaryResponse> listOrders(
            @Parameter(description = "Filter by order status. Allowed values: PENDIENTE_RESERVA, CONFIRMADO, EN_DESPACHO, ENTREGADO, CANCELADO")
            @Pattern(regexp = "^(PENDIENTE_RESERVA|CONFIRMADO|EN_DESPACHO|ENTREGADO|CANCELADO)$",
                    message = "must be one of: PENDIENTE_RESERVA, CONFIRMADO, EN_DESPACHO, ENTREGADO, CANCELADO")
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Authenticated user email (injected by API Gateway)", required = true)
            @RequestHeader("X-User-Email") String userEmail,
            @Parameter(description = "User role: ADMIN or CUSTOMER (injected by API Gateway)", required = true)
            @RequestHeader("X-User-Role") String userRole,
            @Parameter(description = "Zero-based page number", example = "0")
            @Min(value = 0, message = "must be >= 0")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @Positive(message = "must be > 0")
            @RequestParam(name = "size", defaultValue = "20") int size) {
        UUID requesterId = extractUserIdFromEmail(userEmail);
        boolean isAdmin = ROLE_ADMIN.equals(userRole);
        return orderHandler.listOrders(status, requesterId, isAdmin, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Change order status (Admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status value"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Invalid state transition")
    })
    public Mono<ResponseEntity<OrderResponse>> changeOrderStatus(
            @Parameter(description = "Order UUID") @PathVariable("id") UUID orderId,
            @Valid @RequestBody ChangeStatusRequest request,
            @Parameter(description = "Authenticated admin email (injected by API Gateway)", required = true)
            @RequestHeader("X-User-Email") String userEmail) {
        UUID adminId = extractUserIdFromEmail(userEmail);
        return orderHandler.changeOrderStatus(orderId, request, adminId);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be cancelled in current status")
    })
    public Mono<ResponseEntity<OrderResponse>> cancelOrder(
            @Parameter(description = "Order UUID") @PathVariable("id") UUID orderId,
            @Valid @RequestBody CancelOrderRequest request,
            @Parameter(description = "Authenticated user email (injected by API Gateway)", required = true)
            @RequestHeader("X-User-Email") String userEmail,
            @Parameter(description = "User role: ADMIN or CUSTOMER (injected by API Gateway)", required = true)
            @RequestHeader("X-User-Role") String userRole) {
        UUID requesterId = extractUserIdFromEmail(userEmail);
        boolean isAdmin = ROLE_ADMIN.equals(userRole);
        return orderHandler.cancelOrder(orderId, request, requesterId, isAdmin);
    }

    // ──────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────

    private UUID extractUserIdFromEmail(String email) {
        // TODO: In production, this should map email to actual user UUID from user service
        // For now, using a deterministic UUID based on email hash
        return UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));
    }
}
