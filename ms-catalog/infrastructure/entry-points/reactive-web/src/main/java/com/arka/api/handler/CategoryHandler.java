package com.arka.api.handler;

import com.arka.api.dto.CategoryResponse;
import com.arka.api.dto.CreateCategoryRequest;
import com.arka.api.mapper.CategoryMapper;
import com.arka.usecase.category.CategoryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CategoryHandler {

    private final CategoryUseCase categoryUseCase;

    public Mono<ResponseEntity<CategoryResponse>> create(CreateCategoryRequest request) {
        UUID eventId = UUID.randomUUID();
        
        return categoryUseCase.create(eventId, request.name(), request.description())
                .map(CategoryMapper::toResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    public Flux<CategoryResponse> listAll() {
        return categoryUseCase.listAll()
                .map(CategoryMapper::toResponse);
    }
}
