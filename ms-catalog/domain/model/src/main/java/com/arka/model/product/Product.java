package com.arka.model.product;

import com.arka.model.valueobjects.Money;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Product aggregate root representing a catalog item.
 * Products have SKU (String - natural identifier), pricing (cost and price), 
 * category reference (UUID), and nested reviews.
 */
@Builder(toBuilder = true)
public record Product(
    UUID id,
    String sku,
    String name,
    String description,
    Money cost,
    Money price,
    UUID categoryId,
    boolean active,
    List<Review> reviews,
    Instant createdAt,
    Instant updatedAt
) {
    public Product {
        Objects.requireNonNull(sku, "SKU is required");
        if (sku.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be blank");
        }
        
        Objects.requireNonNull(name, "Name is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        
        Objects.requireNonNull(categoryId, "CategoryId is required");
        Objects.requireNonNull(price, "Price is required");

        // Business rule: price must be positive
        if (!price.isPositive()) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }

        // Business rule: price must be strictly greater than cost
        if (cost != null && !price.isGreaterThan(cost)) {
            throw new IllegalArgumentException("Price must be strictly greater than cost");
        }

        // Ensure reviews list is never null and is immutable
        reviews = reviews != null ? List.copyOf(reviews) : List.of();
    }

    /**
     * Domain logic: Add a review to this product.
     * Returns a new immutable Product instance with the review added.
     *
     * @param newReview the review to add
     * @return a new Product instance with the review added
     */
    public Product addReview(Review newReview) {
        List<Review> updatedReviews = new ArrayList<>(this.reviews);
        updatedReviews.add(newReview);
        return this.toBuilder()
                   .reviews(List.copyOf(updatedReviews))
                   .build();
    }
}
