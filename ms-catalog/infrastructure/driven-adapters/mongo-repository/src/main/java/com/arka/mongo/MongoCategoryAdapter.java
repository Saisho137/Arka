package com.arka.mongo;

import com.arka.model.category.Category;
import com.arka.model.category.gateways.CategoryRepository;
import com.arka.mongo.document.CategoryDocument;
import com.arka.mongo.mapper.CategoryDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB adapter implementing CategoryRepository port.
 * Uses ReactiveMongoTemplate for all operations.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoCategoryAdapter implements CategoryRepository {
    
    private final ReactiveMongoTemplate mongoTemplate;
    
    @Override
    public Mono<Category> save(Category category) {
        log.debug("Saving category with name: {}", category.name());
        
        CategoryDocument document = CategoryDocumentMapper.toDocument(category);
        
        // Set createdAt if not present
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(Instant.now());
        }
        
        return mongoTemplate.save(document)
                .map(CategoryDocumentMapper::toDomain)
                .doOnSuccess(c -> log.debug("Category saved with ID: {}", c.id()));
    }
    
    @Override
    public Mono<Category> findById(UUID id) {
        log.debug("Finding category by ID: {}", id);
        
        Query query = Query.query(Criteria.where("_id").is(id));
        
        return mongoTemplate.findOne(query, CategoryDocument.class)
                .map(CategoryDocumentMapper::toDomain)
                .doOnSuccess(c -> {
                    if (c != null) {
                        log.debug("Category found with ID: {}", id);
                    } else {
                        log.debug("Category not found with ID: {}", id);
                    }
                });
    }
    
    @Override
    public Mono<Category> findByName(String name) {
        log.debug("Finding category by name: {}", name);
        
        Query query = Query.query(Criteria.where("name").is(name));
        
        return mongoTemplate.findOne(query, CategoryDocument.class)
                .map(CategoryDocumentMapper::toDomain)
                .doOnSuccess(c -> {
                    if (c != null) {
                        log.debug("Category found with name: {}", name);
                    } else {
                        log.debug("Category not found with name: {}", name);
                    }
                });
    }
    
    @Override
    public Flux<Category> findAll() {
        log.debug("Finding all categories");
        
        return mongoTemplate.findAll(CategoryDocument.class)
                .map(CategoryDocumentMapper::toDomain)
                .doOnComplete(() -> log.debug("Completed finding all categories"));
    }
    
    @Override
    public Mono<Category> deactivate(UUID id) {
        log.debug("Deactivating category with ID: {}", id);
        
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update().set("active", false);
        
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        
        return mongoTemplate.findAndModify(query, update, options, CategoryDocument.class)
                .map(CategoryDocumentMapper::toDomain)
                .doOnSuccess(c -> {
                    if (c != null) {
                        log.debug("Category deactivated with ID: {}", id);
                    } else {
                        log.warn("Category not found for deactivation with ID: {}", id);
                    }
                });
    }
}
