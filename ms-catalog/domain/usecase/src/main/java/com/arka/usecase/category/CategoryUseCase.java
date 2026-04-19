package com.arka.usecase.category;

import com.arka.model.category.Category;
import com.arka.model.category.gateways.CategoryRepository;
import com.arka.model.commons.exception.CategoryNotFoundException;
import com.arka.model.commons.exception.DuplicateCategoryException;
import com.arka.model.commons.exception.InvalidCategoryStateException;
import com.arka.model.commons.gateways.TransactionalGateway;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final TransactionalGateway transactionalGateway;

    public Mono<Category> create(UUID eventId, String name, String description) {
        Mono<Category> pipeline = processedEventRepository.exists(eventId)
                .flatMap(alreadyProcessed -> {
                    if (Boolean.TRUE.equals(alreadyProcessed)) {
                        return categoryRepository.findByName(name)
                                .switchIfEmpty(Mono.error(new CategoryNotFoundException("name", name)));
                    }

                    return categoryRepository.findByName(name)
                            .flatMap(existing -> Mono.error(new DuplicateCategoryException(name)))
                            .switchIfEmpty(Mono.defer(() -> {
                                Category newCategory = Category.builder()
                                        .id(UUID.randomUUID())
                                        .name(name)
                                        .description(description)
                                        .active(true)
                                        .createdAt(Instant.now())
                                        .build();

                                return categoryRepository.save(newCategory)
                                        .flatMap(saved -> processedEventRepository.save(eventId)
                                                .thenReturn(saved));
                            }));
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    public Flux<Category> listAll() {
        return categoryRepository.findAll();
    }

    public Mono<Category> findById(UUID id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new CategoryNotFoundException(id)));
    }

    public Mono<Category> deactivate(UUID id) {
        return categoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new CategoryNotFoundException(id)))
                .flatMap(category -> {
                    if (!category.active()) {
                        return Mono.error(new InvalidCategoryStateException(id, "deactivate", "INACTIVE"));
                    }

                    return categoryRepository.deactivate(id);
                });
    }
}
