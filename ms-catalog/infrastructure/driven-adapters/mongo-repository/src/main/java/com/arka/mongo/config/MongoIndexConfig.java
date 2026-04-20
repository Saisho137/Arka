package com.arka.mongo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * MongoDB index configuration.
 * Creates indexes on startup for optimal query performance.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {
    
    @Bean
    public CommandLineRunner createMongoIndexes(ReactiveMongoTemplate mongoTemplate) {
        return args -> {
            log.info("Creating MongoDB indexes...");

            // Product indexes
            mongoTemplate.indexOps("products")
                    .ensureIndex(new Index().on("sku", Sort.Direction.ASC).unique())
                    .then(mongoTemplate.indexOps("products")
                            .ensureIndex(new Index().on("categoryId", Sort.Direction.ASC)))
                    .then(mongoTemplate.indexOps("products")
                            .ensureIndex(new Index().on("active", Sort.Direction.ASC)))

                    // Category indexes
                    .then(mongoTemplate.indexOps("categories")
                            .ensureIndex(new Index().on("name", Sort.Direction.ASC).unique()))

                    // Outbox event indexes
                    .then(mongoTemplate.indexOps("outbox_events")
                            .ensureIndex(new Index()
                                    .on("status", Sort.Direction.ASC)
                                    .on("createdAt", Sort.Direction.ASC)))
                    .then(mongoTemplate.indexOps("outbox_events")
                            .ensureIndex(new Index().on("eventId", Sort.Direction.ASC).unique()))

                    .doOnSuccess(v -> log.info("MongoDB indexes created successfully"))
                    .doOnError(e -> log.error("Error creating MongoDB indexes", e))
                    .block();
        };
    }
}
