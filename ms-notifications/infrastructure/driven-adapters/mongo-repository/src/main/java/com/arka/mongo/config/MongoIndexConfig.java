package com.arka.mongo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoIndexConfig {

    @Bean
    public CommandLineRunner createNotificationIndexes(ReactiveMongoTemplate mongoTemplate) {
        return args -> {
            log.info("Creating MongoDB indexes for ms-notifications...");

            // notification_history:
            //   - unique index on eventId → prevents duplicate processing
            //   - TTL index on processedAt (90 days) → auto-cleanup of old records
            //   - index on status → efficient queries by notification outcome
            mongoTemplate.indexOps("notification_history")
                    .ensureIndex(new Index().on("eventId", Sort.Direction.ASC).unique())
                    .then(mongoTemplate.indexOps("notification_history")
                            .ensureIndex(new Index().on("processedAt", Sort.Direction.ASC)
                                    .expire(Duration.ofDays(90))))
                    .then(mongoTemplate.indexOps("notification_history")
                            .ensureIndex(new Index().on("status", Sort.Direction.ASC)))

                    .doOnSuccess(v -> log.info("MongoDB indexes created successfully"))
                    .onErrorResume(e -> {
                        log.warn("MongoDB index creation failed (non-fatal): {}", e.getMessage());
                        return reactor.core.publisher.Mono.empty();
                    })
                    .block();
        };
    }
}

