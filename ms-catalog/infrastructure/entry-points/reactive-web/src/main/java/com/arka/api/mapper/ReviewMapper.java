package com.arka.api.mapper;

import com.arka.api.dto.AddReviewRequest;
import com.arka.api.dto.ReviewResponse;
import com.arka.model.product.Review;

import java.time.Instant;
import java.util.UUID;

public final class ReviewMapper {

    private ReviewMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Review toDomain(AddReviewRequest request, UUID reviewId) {
        return Review.builder()
                .reviewId(reviewId)
                .userId(request.userId())
                .rating(request.rating())
                .comment(request.comment())
                .createdAt(Instant.now())
                .build();
    }

    public static ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
                .reviewId(review.reviewId())
                .userId(review.userId())
                .rating(review.rating())
                .comment(review.comment())
                .createdAt(review.createdAt())
                .build();
    }
}
