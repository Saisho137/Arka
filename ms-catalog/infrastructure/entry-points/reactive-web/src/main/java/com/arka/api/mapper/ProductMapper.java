package com.arka.api.mapper;

import com.arka.api.dto.CreateProductRequest;
import com.arka.api.dto.ProductResponse;
import com.arka.api.dto.UpdateProductRequest;
import com.arka.model.product.Product;
import com.arka.model.valueobjects.Money;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ProductMapper {

    private ProductMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Product toDomain(CreateProductRequest request, UUID productId) {
        return Product.builder()
                .id(productId)
                .sku(request.sku())
                .name(request.name())
                .description(request.description())
                .cost(Money.builder()
                        .amount(request.cost())
                        .currency(request.currency())
                        .build())
                .price(Money.builder()
                        .amount(request.price())
                        .currency(request.currency())
                        .build())
                .categoryId(UUID.fromString(request.categoryId()))
                .active(true)
                .reviews(java.util.List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static Product toDomain(UpdateProductRequest request) {
        return Product.builder()
                .name(request.name())
                .description(request.description())
                .cost(Money.builder()
                        .amount(request.cost())
                        .currency(request.currency())
                        .build())
                .price(Money.builder()
                        .amount(request.price())
                        .currency(request.currency())
                        .build())
                .categoryId(UUID.fromString(request.categoryId()))
                .build();
    }

    public static ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.id())
                .sku(product.sku())
                .name(product.name())
                .description(product.description())
                .cost(product.cost().amount())
                .price(product.price().amount())
                .currency(product.price().currency())
                .categoryId(product.categoryId())
                .categoryName(null) // Will be enriched by handler if needed
                .active(product.active())
                .reviews(product.reviews().stream()
                        .map(ReviewMapper::toResponse)
                        .collect(Collectors.toList()))
                .createdAt(product.createdAt())
                .updatedAt(product.updatedAt())
                .build();
    }
}
