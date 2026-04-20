package com.arka.mongo.mapper;

import com.arka.model.product.Product;
import com.arka.model.valueobjects.Money;
import com.arka.mongo.document.ProductDocument;

import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Static mapper for converting between Product domain model and ProductDocument.
 */
public final class ProductDocumentMapper {
    
    private ProductDocumentMapper() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Converts Product domain model to ProductDocument for MongoDB persistence.
     *
     * @param product the domain model
     * @return the MongoDB document
     */
    public static ProductDocument toDocument(Product product) {
        if (product == null) {
            return null;
        }
        
        return ProductDocument.builder()
                .id(product.id())
                .sku(product.sku())
                .name(product.name())
                .description(product.description())
                .cost(product.cost() != null ? product.cost().amount() : null)
                .price(product.price().amount())
                .currency(product.price().currency())
                .categoryId(product.categoryId())
                .active(product.active())
                .reviews(product.reviews() != null 
                    ? product.reviews().stream()
                        .map(ReviewDocumentMapper::toDocument)
                        .collect(Collectors.toList())
                    : Collections.emptyList())
                .createdAt(product.createdAt())
                .updatedAt(product.updatedAt())
                .build();
    }
    
    /**
     * Converts ProductDocument from MongoDB to Product domain model.
     *
     * @param document the MongoDB document
     * @return the domain model
     */
    public static Product toDomain(ProductDocument document) {
        if (document == null) {
            return null;
        }
        
        Money cost = document.getCost() != null 
            ? Money.builder()
                .amount(document.getCost())
                .currency(document.getCurrency())
                .build()
            : null;
        
        Money price = Money.builder()
                .amount(document.getPrice())
                .currency(document.getCurrency())
                .build();
        
        return Product.builder()
                .id(document.getId())
                .sku(document.getSku())
                .name(document.getName())
                .description(document.getDescription())
                .cost(cost)
                .price(price)
                .categoryId(document.getCategoryId())
                .active(document.isActive())
                .reviews(document.getReviews() != null
                    ? document.getReviews().stream()
                        .map(ReviewDocumentMapper::toDomain)
                        .collect(Collectors.toList())
                    : Collections.emptyList())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
