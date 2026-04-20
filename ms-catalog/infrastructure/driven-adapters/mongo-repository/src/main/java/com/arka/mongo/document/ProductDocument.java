package com.arka.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB document for Product aggregate.
 * Stores products with nested reviews as subdocuments.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class ProductDocument {
    
    @Id
    private UUID id;
    
    @Indexed(unique = true)
    private String sku;
    
    private String name;
    
    private String description;
    
    private BigDecimal cost;
    
    private BigDecimal price;
    
    private String currency;
    
    @Indexed
    private UUID categoryId;
    
    @Indexed
    private boolean active;
    
    private List<ReviewDocument> reviews;
    
    private Instant createdAt;
    
    private Instant updatedAt;
}
