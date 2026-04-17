package com.arka.model.product.gateways;

import com.arka.model.product.Product;
import reactor.core.publisher.Mono;

public interface ProductCachePort {

    Mono<Product> get(String key);

    Mono<Void> put(String key, Product product);

    Mono<Void> evict(String key);

    Mono<Void> evictProductListCache();
}
