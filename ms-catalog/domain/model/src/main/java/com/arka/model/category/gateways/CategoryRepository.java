package com.arka.model.category.gateways;

import com.arka.model.category.Category;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CategoryRepository {

    Mono<Category> save(Category category);

    Mono<Category> findById(UUID id);

    Mono<Category> findByName(String name);

    Flux<Category> findAll();

    Mono<Category> deactivate(UUID id);
}
