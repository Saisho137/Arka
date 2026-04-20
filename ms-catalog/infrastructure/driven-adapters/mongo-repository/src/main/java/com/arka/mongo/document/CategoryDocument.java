package com.arka.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB document for Category aggregate.
 * Categories are master data used to organize products.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "categories")
public class CategoryDocument {
    
    @Id
    private UUID id;
    
    @Indexed(unique = true)
    private String name;
    
    private String description;
    
    private boolean active;
    
    private Instant createdAt;
}
