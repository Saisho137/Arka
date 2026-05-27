package com.arka.usecase.cart;

import com.arka.model.cart.*;
import com.arka.model.cart.gateways.CartEventPublisher;
import com.arka.model.cart.gateways.CartRepository;
import com.arka.model.cart.gateways.ProductPriceGateway;
import com.arka.model.commons.exception.*;
import com.arka.model.event.CartAbandonedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class CartUseCase {

    private final CartRepository cartRepository;
    private final ProductPriceGateway productPriceGateway;
    private final CartEventPublisher cartEventPublisher;

    public Mono<Cart> createCart(String customerId) {
        var now = Instant.now();
        var cart = Cart.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .items(List.of())
                .status(CartStatus.ACTIVE)
                .createdAt(now)
                .lastModifiedAt(now)
                .build();
        return cartRepository.save(cart);
    }

    public Mono<Cart> getCart(UUID cartId, String customerId, boolean isAdmin) {
        Mono<Cart> cartMono = isAdmin
                ? cartRepository.findById(cartId)
                : cartRepository.findByIdAndCustomerId(cartId, customerId);
        return cartMono.switchIfEmpty(Mono.error(new CartNotFoundException(cartId.toString())));
    }

    public Flux<Cart> getCartsByCustomer(String customerId, CartStatus status, boolean isAdmin) {
        if (status != null) {
            return cartRepository.findByCustomerIdAndStatus(customerId, status);
        }
        return cartRepository.findByCustomerId(customerId);
    }

    public Mono<Cart> addItem(UUID cartId, String sku, int quantity, String customerId) {
        return cartRepository.findByIdAndCustomerId(cartId, customerId)
                .switchIfEmpty(Mono.error(new CartNotFoundException(cartId.toString())))
                .flatMap(cart -> {
                    if (cart.isCheckedOut()) {
                        return Mono.error(new CartAlreadyCheckedOutException(cartId.toString()));
                    }
                    return productPriceGateway.getProductInfo(sku)
                            .switchIfEmpty(Mono.error(new ProductNotFoundException(sku)))
                            .flatMap(productInfo -> {
                                var item = CartItem.builder()
                                        .sku(sku)
                                        .productName(productInfo.name())
                                        .quantity(quantity)
                                        .unitPrice(productInfo.price())
                                        .addedAt(Instant.now())
                                        .build();
                                return cartRepository.addItem(cartId, item);
                            });
                });
    }

    public Mono<Cart> removeItem(UUID cartId, String sku, String customerId) {
        return cartRepository.findByIdAndCustomerId(cartId, customerId)
                .switchIfEmpty(Mono.error(new CartNotFoundException(cartId.toString())))
                .flatMap(cart -> {
                    if (cart.isCheckedOut()) {
                        return Mono.error(new CartAlreadyCheckedOutException(cartId.toString()));
                    }
                    boolean itemExists = cart.items().stream().anyMatch(i -> i.sku().equals(sku));
                    if (!itemExists) {
                        return Mono.error(new CartItemNotFoundException(sku));
                    }
                    return cartRepository.removeItem(cartId, sku);
                });
    }

    public Mono<Cart> updateItemQuantity(UUID cartId, String sku, int newQuantity, String customerId) {
        return cartRepository.findByIdAndCustomerId(cartId, customerId)
                .switchIfEmpty(Mono.error(new CartNotFoundException(cartId.toString())))
                .flatMap(cart -> {
                    if (cart.isCheckedOut()) {
                        return Mono.error(new CartAlreadyCheckedOutException(cartId.toString()));
                    }
                    boolean itemExists = cart.items().stream().anyMatch(i -> i.sku().equals(sku));
                    if (!itemExists) {
                        return Mono.error(new CartItemNotFoundException(sku));
                    }
                    return cartRepository.updateItemQuantity(cartId, sku, newQuantity);
                });
    }

    public Mono<Cart> clearCart(UUID cartId, String customerId) {
        return cartRepository.findByIdAndCustomerId(cartId, customerId)
                .switchIfEmpty(Mono.error(new CartNotFoundException(cartId.toString())))
                .flatMap(cart -> {
                    if (cart.isCheckedOut()) {
                        return Mono.error(new CartAlreadyCheckedOutException(cartId.toString()));
                    }
                    return cartRepository.clearItems(cartId);
                });
    }

    public Mono<Void> deleteCart(UUID cartId, String customerId, boolean isAdmin) {
        Mono<Cart> cartMono = isAdmin
                ? cartRepository.findById(cartId)
                : cartRepository.findByIdAndCustomerId(cartId, customerId);
        return cartMono
                .switchIfEmpty(Mono.error(new CartNotFoundException(cartId.toString())))
                .flatMap(cart -> cartRepository.deleteById(cartId));
    }

    public Mono<CheckoutResult> checkout(UUID cartId, String customerId) {
        return cartRepository.findByIdAndCustomerId(cartId, customerId)
                .switchIfEmpty(Mono.error(new CartNotFoundException(cartId.toString())))
                .flatMap(cart -> {
                    if (cart.isEmpty()) {
                        return Mono.error(new EmptyCartException(cartId.toString()));
                    }
                    if (cart.isCheckedOut()) {
                        return Mono.error(new CartAlreadyCheckedOutException(cartId.toString()));
                    }
                    return validatePrices(cart)
                            .flatMap(result -> {
                                if (result.status() == CheckoutStatus.READY) {
                                    return cartRepository.markAsCheckedOut(cartId)
                                            .thenReturn(result);
                                }
                                return Mono.just(result);
                            });
                });
    }

    public Mono<Integer> detectAbandonedCarts(Duration threshold) {
        var cutoffTime = Instant.now().minus(threshold);
        return cartRepository.findAbandonedCarts(cutoffTime)
                .flatMap(cart -> cartRepository.markAsAbandoned(cart.id())
                        .flatMap(abandoned -> {
                            var event = CartAbandonedEvent.builder()
                                    .cartId(abandoned.id())
                                    .customerId(abandoned.customerId())
                                    .itemCount(abandoned.items().size())
                                    .totalAmount(abandoned.calculateTotal())
                                    .abandonedAt(Instant.now())
                                    .lastModifiedAt(abandoned.lastModifiedAt())
                                    .build();
                            return cartEventPublisher.publishCartAbandoned(event)
                                    .thenReturn(1);
                        })
                        .onErrorResume(e -> {
                            log.error("Failed to process abandoned cart {}: {}", cart.id(), e.getMessage());
                            return Mono.just(0);
                        })
                )
                .reduce(0, Integer::sum);
    }

    private Mono<CheckoutResult> validatePrices(Cart cart) {
        return Flux.fromIterable(cart.items())
                .flatMap(item -> productPriceGateway.getProductInfo(item.sku())
                        .switchIfEmpty(Mono.error(new ProductNotFoundException(item.sku())))
                        .map(productInfo -> {
                            if (productInfo.price().compareTo(item.unitPrice()) != 0) {
                                return new PriceChange(item.sku(), item.unitPrice(), productInfo.price());
                            }
                            return null;
                        })
                )
                .filter(pc -> pc != null)
                .collectList()
                .map(priceChanges -> {
                    BigDecimal total = cart.items().stream()
                            .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    CheckoutStatus status = priceChanges.isEmpty()
                            ? CheckoutStatus.READY
                            : CheckoutStatus.PRICE_CHANGED;
                    return CheckoutResult.builder()
                            .cartId(cart.id())
                            .priceChanges(priceChanges)
                            .totalAmount(total)
                            .status(status)
                            .build();
                });
    }
}
