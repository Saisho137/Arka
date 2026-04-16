package com.arka.model.product;

import lombok.Builder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Value Object representing a product review stored as a subdocument in MongoDB.
 * Reviews are nested within Product documents and include automatic UUID generation.
 */
@Builder(toBuilder = true)
public record Review(
    UUID reviewId,
    String userId,
    int rating,
    String comment,
    Instant createdAt
) {
    public Review {
        // Auto-generate reviewId if not provided
        reviewId = reviewId != null ? reviewId : UUID.randomUUID();
        
        Objects.requireNonNull(userId, "userId is required");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
        
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        
        Objects.requireNonNull(comment, "comment is required");
        if (comment.isBlank()) {
            throw new IllegalArgumentException("comment cannot be blank");
        }
        
        // Auto-generate createdAt if not provided
        createdAt = createdAt != null ? createdAt : Instant.now();
    }
}
