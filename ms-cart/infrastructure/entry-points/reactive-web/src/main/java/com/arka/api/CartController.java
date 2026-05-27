package com.arka.api;

import com.arka.api.dto.*;
import com.arka.api.mapper.CartDtoMapper;
import com.arka.model.cart.CartStatus;
import com.arka.usecase.cart.CartUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@Tag(name = "Carts")
public class CartController {

    private final CartUseCase cartUseCase;

    @PostMapping
    @Operation(summary = "Create a new cart")
    public Mono<ResponseEntity<CartResponse>> createCart(
            @Valid @RequestBody CreateCartRequest request) {
        return cartUseCase.createCart(request.customerId())
                .map(cart -> ResponseEntity.status(HttpStatus.CREATED).body(CartDtoMapper.toResponse(cart)));
    }

    @GetMapping("/{cartId}")
    @Operation(summary = "Get cart by ID")
    public Mono<ResponseEntity<CartResponse>> getCart(
            @PathVariable UUID cartId,
            @RequestHeader("X-User-Email") String customerId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        return cartUseCase.getCart(cartId, customerId, isAdmin)
                .map(cart -> ResponseEntity.ok(CartDtoMapper.toResponse(cart)));
    }

    @GetMapping
    @Operation(summary = "Get carts by customer")
    public Flux<CartResponse> getCartsByCustomer(
            @RequestHeader("X-User-Email") String customerId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role,
            @RequestParam(required = false) String status) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        CartStatus cartStatus = status != null ? CartStatus.valueOf(status) : null;
        return cartUseCase.getCartsByCustomer(customerId, cartStatus, isAdmin)
                .map(CartDtoMapper::toResponse);
    }

    @PostMapping("/{cartId}/items")
    @Operation(summary = "Add item to cart")
    public Mono<ResponseEntity<CartResponse>> addItem(
            @PathVariable UUID cartId,
            @Valid @RequestBody AddItemRequest request,
            @RequestHeader("X-User-Email") String customerId) {
        return cartUseCase.addItem(cartId, request.sku(), request.quantity(), customerId)
                .map(cart -> ResponseEntity.ok(CartDtoMapper.toResponse(cart)));
    }

    @DeleteMapping("/{cartId}/items/{sku}")
    @Operation(summary = "Remove item from cart")
    public Mono<ResponseEntity<CartResponse>> removeItem(
            @PathVariable UUID cartId,
            @PathVariable String sku,
            @RequestHeader("X-User-Email") String customerId) {
        return cartUseCase.removeItem(cartId, sku, customerId)
                .map(cart -> ResponseEntity.ok(CartDtoMapper.toResponse(cart)));
    }

    @PutMapping("/{cartId}/items/{sku}")
    @Operation(summary = "Update item quantity")
    public Mono<ResponseEntity<CartResponse>> updateItemQuantity(
            @PathVariable UUID cartId,
            @PathVariable String sku,
            @Valid @RequestBody UpdateItemQuantityRequest request,
            @RequestHeader("X-User-Email") String customerId) {
        return cartUseCase.updateItemQuantity(cartId, sku, request.quantity(), customerId)
                .map(cart -> ResponseEntity.ok(CartDtoMapper.toResponse(cart)));
    }

    @DeleteMapping("/{cartId}/items")
    @Operation(summary = "Clear all items from cart")
    public Mono<ResponseEntity<CartResponse>> clearCart(
            @PathVariable UUID cartId,
            @RequestHeader("X-User-Email") String customerId) {
        return cartUseCase.clearCart(cartId, customerId)
                .map(cart -> ResponseEntity.ok(CartDtoMapper.toResponse(cart)));
    }

    @DeleteMapping("/{cartId}")
    @Operation(summary = "Delete cart")
    public Mono<ResponseEntity<Void>> deleteCart(
            @PathVariable UUID cartId,
            @RequestHeader("X-User-Email") String customerId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role) {
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        return cartUseCase.deleteCart(cartId, customerId, isAdmin)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @PostMapping("/{cartId}/checkout")
    @Operation(summary = "Checkout cart — validates prices with catalog")
    public Mono<ResponseEntity<CheckoutResponseDto>> checkout(
            @PathVariable UUID cartId,
            @RequestHeader("X-User-Email") String customerId) {
        return cartUseCase.checkout(cartId, customerId)
                .map(result -> ResponseEntity.ok(CartDtoMapper.toCheckoutResponse(result)));
    }
}
