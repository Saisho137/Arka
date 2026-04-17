package com.arka.usecase.category;

import com.arka.model.category.Category;
import com.arka.model.category.gateways.CategoryRepository;
import com.arka.model.commons.exception.DuplicateCategoryException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CategoryUseCase {

    private final CategoryRepository categoryRepository;

    public Mono<Category> create(String name, String description) {
        return categoryRepository.findByName(name)
                .flatMap(existing -> Mono.<Category>error(new DuplicateCategoryException(name)))
                .switchIfEmpty(Mono.defer(() -> {
                    Category newCategory = Category.builder()
                            .id(UUID.randomUUID())
                            .name(name)
                            .description(description)
                            .active(true)
                            .createdAt(Instant.now())
                            .build();

                    return categoryRepository.save(newCategory);
                }));
    }

    public Flux<Category> listAll() {
        return categoryRepository.findAll();
    }
}
