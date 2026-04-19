package com.arka.api.handler;

import com.arka.api.dto.AddReviewRequest;
import com.arka.api.dto.CreateProductRequest;
import com.arka.api.dto.ProductResponse;
import com.arka.api.dto.UpdateProductRequest;
import com.arka.api.mapper.ProductMapper;
import com.arka.api.mapper.ReviewMapper;
import com.arka.model.product.Product;
import com.arka.model.product.Review;
import com.arka.usecase.product.ProductUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductHandler {

    private final ProductUseCase productUseCase;

    public Mono<ResponseEntity<ProductResponse>> create(CreateProductRequest request) {
        UUID idempotencyKey = UUID.randomUUID();
        Product product = ProductMapper.toDomain(request, idempotencyKey);

        return productUseCase.create(idempotencyKey, product, request.initialStock())
                .map(ProductMapper::toResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }


    public Mono<ResponseEntity<ProductResponse>> getById(UUID id) {
        return productUseCase.getById(id)
                .map(ProductMapper::toResponse)
                .map(ResponseEntity::ok);
    }


    public Flux<ProductResponse> listActive(int page, int size) {
        return productUseCase.listActive(page, size)
                .map(ProductMapper::toResponse);
    }

    public Mono<ResponseEntity<ProductResponse>> update(UUID id, UpdateProductRequest request) {
        Product updatedProduct = ProductMapper.toDomain(request);

        return productUseCase.update(id, updatedProduct)
                .map(ProductMapper::toResponse)
                .map(ResponseEntity::ok);
    }


    public Mono<ResponseEntity<Void>> deactivate(UUID id) {
        return productUseCase.deactivate(id)
                .then(Mono.just(ResponseEntity.ok().build()));
    }


    public Mono<ResponseEntity<ProductResponse>> addReview(UUID productId, AddReviewRequest request) {
        UUID idempotencyKey = UUID.randomUUID();
        Review review = ReviewMapper.toDomain(request, idempotencyKey);

        return productUseCase.addReview(idempotencyKey, productId, review)
                .map(ProductMapper::toResponse)
                .map(ResponseEntity::ok);
    }
}
