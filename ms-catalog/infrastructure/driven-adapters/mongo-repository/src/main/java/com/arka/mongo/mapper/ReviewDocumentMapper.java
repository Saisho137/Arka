package com.arka.mongo.mapper;

import com.arka.model.product.Review;
import com.arka.mongo.document.ReviewDocument;

/**
 * Static mapper for converting between Review domain model and ReviewDocument.
 */
public final class ReviewDocumentMapper {
    
    private ReviewDocumentMapper() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Converts Review domain model to ReviewDocument for MongoDB persistence.
     *
     * @param review the domain model
     * @return the MongoDB subdocument
     */
    public static ReviewDocument toDocument(Review review) {
        if (review == null) {
            return null;
        }
        
        return ReviewDocument.builder()
                .reviewId(review.reviewId())
                .userId(review.userId())
                .rating(review.rating())
                .comment(review.comment())
                .createdAt(review.createdAt())
                .build();
    }
    
    /**
     * Converts ReviewDocument from MongoDB to Review domain model.
     *
     * @param document the MongoDB subdocument
     * @return the domain model
     */
    public static Review toDomain(ReviewDocument document) {
        if (document == null) {
            return null;
        }
        
        return Review.builder()
                .reviewId(document.getReviewId())
                .userId(document.getUserId())
                .rating(document.getRating())
                .comment(document.getComment())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
