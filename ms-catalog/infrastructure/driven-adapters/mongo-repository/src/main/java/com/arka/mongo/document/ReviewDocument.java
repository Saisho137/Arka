package com.arka.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB subdocument for Review.
 * Stored as nested array within ProductDocument.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDocument {
    
    private UUID reviewId;
    
    private String userId;
    
    private int rating;
    
    private String comment;
    
    private Instant createdAt;
}
