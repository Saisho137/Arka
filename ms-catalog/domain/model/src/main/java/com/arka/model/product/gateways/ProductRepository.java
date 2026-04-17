package com.arka.model.product.gateways;

import com.arka.model.product.Product;
import com.arka.model.product.Review;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProductRepository {
    
    Mono<Product> save(Product product);
    
    Mono<Product> findById(UUID id);
    
    Mono<Product> findBySku(String sku);
    
    Flux<Product> findAllActive(int page, int size);
    
    Mono<Product> update(Product product);
    
    Mono<Product> deactivate(UUID id);
    
    Mono<Product> addReview(UUID productId, Review review);
}
