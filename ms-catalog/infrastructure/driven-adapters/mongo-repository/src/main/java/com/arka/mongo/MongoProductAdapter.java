package com.arka.mongo;

import com.arka.model.product.Product;
import com.arka.model.product.Review;
import com.arka.model.product.gateways.ProductRepository;
import com.arka.mongo.document.ProductDocument;
import com.arka.mongo.document.ReviewDocument;
import com.arka.mongo.mapper.ProductDocumentMapper;
import com.arka.mongo.mapper.ReviewDocumentMapper;
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
 * MongoDB adapter implementing ProductRepository port.
 * Uses ReactiveMongoTemplate for all operations with atomic updates.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoProductAdapter implements ProductRepository {
    
    private final ReactiveMongoTemplate mongoTemplate;
    
    @Override
    public Mono<Product> save(Product product) {
        log.debug("Saving product with SKU: {}", product.sku());
        
        ProductDocument document = ProductDocumentMapper.toDocument(product);
        
        // Set timestamps if not present
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(Instant.now());
        }
        document.setUpdatedAt(Instant.now());
        
        return mongoTemplate.save(document)
                .map(ProductDocumentMapper::toDomain)
                .doOnSuccess(p -> log.debug("Product saved with ID: {}", p.id()));
    }
    
    @Override
    public Mono<Product> findById(UUID id) {
        log.debug("Finding product by ID: {}", id);
        
        Query query = Query.query(Criteria.where("_id").is(id));
        
        return mongoTemplate.findOne(query, ProductDocument.class)
                .map(ProductDocumentMapper::toDomain)
                .doOnSuccess(p -> {
                    if (p != null) {
                        log.debug("Product found with ID: {}", id);
                    } else {
                        log.debug("Product not found with ID: {}", id);
                    }
                });
    }
    
    @Override
    public Mono<Product> findBySku(String sku) {
        log.debug("Finding product by SKU: {}", sku);
        
        Query query = Query.query(Criteria.where("sku").is(sku));
        
        return mongoTemplate.findOne(query, ProductDocument.class)
                .map(ProductDocumentMapper::toDomain)
                .doOnSuccess(p -> {
                    if (p != null) {
                        log.debug("Product found with SKU: {}", sku);
                    } else {
                        log.debug("Product not found with SKU: {}", sku);
                    }
                });
    }
    
    @Override
    public Flux<Product> findAllActive(int page, int size) {
        log.debug("Finding active products - page: {}, size: {}", page, size);
        
        Query query = Query.query(Criteria.where("active").is(true))
                .skip((long) page * size)
                .limit(size);
        
        return mongoTemplate.find(query, ProductDocument.class)
                .map(ProductDocumentMapper::toDomain)
                .doOnComplete(() -> log.debug("Completed finding active products"));
    }
    
    @Override
    public Mono<Product> update(Product product) {
        log.debug("Updating product with ID: {}", product.id());
        
        ProductDocument document = ProductDocumentMapper.toDocument(product);
        document.setUpdatedAt(Instant.now());
        
        return mongoTemplate.save(document)
                .map(ProductDocumentMapper::toDomain)
                .doOnSuccess(p -> log.debug("Product updated with ID: {}", p.id()));
    }
    
    @Override
    public Mono<Product> deactivate(UUID id) {
        log.debug("Deactivating product with ID: {}", id);
        
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("active", false)
                .set("updatedAt", Instant.now());
        
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        
        return mongoTemplate.findAndModify(query, update, options, ProductDocument.class)
                .map(ProductDocumentMapper::toDomain)
                .doOnSuccess(p -> {
                    if (p != null) {
                        log.debug("Product deactivated with ID: {}", id);
                    } else {
                        log.warn("Product not found for deactivation with ID: {}", id);
                    }
                });
    }
    
    @Override
    public Mono<Product> addReview(UUID productId, Review review) {
        log.debug("Adding review to product with ID: {}", productId);
        
        Query query = Query.query(Criteria.where("_id").is(productId));
        
        ReviewDocument reviewDocument = ReviewDocumentMapper.toDocument(review);
        
        Update update = new Update()
                .push("reviews", reviewDocument)
                .set("updatedAt", Instant.now());
        
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        
        return mongoTemplate.findAndModify(query, update, options, ProductDocument.class)
                .map(ProductDocumentMapper::toDomain)
                .doOnSuccess(p -> {
                    if (p != null) {
                        log.debug("Review added to product with ID: {}", productId);
                    } else {
                        log.warn("Product not found for adding review with ID: {}", productId);
                    }
                });
    }
}
