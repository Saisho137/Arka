package com.arka.model.cart.gateways;

import com.arka.model.cart.Cart;
import com.arka.model.cart.CartItem;
import com.arka.model.cart.CartStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface CartRepository {

    Mono<Cart> save(Cart cart);

    Mono<Cart> findById(UUID cartId);

    Mono<Cart> findByIdAndCustomerId(UUID cartId, String customerId);

    Flux<Cart> findByCustomerId(String customerId);

    Flux<Cart> findByCustomerIdAndStatus(String customerId, CartStatus status);

    Mono<Cart> addItem(UUID cartId, CartItem item);

    Mono<Cart> updateItemQuantity(UUID cartId, String sku, int quantity);

    Mono<Cart> removeItem(UUID cartId, String sku);

    Mono<Cart> clearItems(UUID cartId);

    Mono<Void> deleteById(UUID cartId);

    Mono<Cart> markAsCheckedOut(UUID cartId);

    Flux<Cart> findAbandonedCarts(Instant threshold);

    Mono<Cart> markAsAbandoned(UUID cartId);
}
