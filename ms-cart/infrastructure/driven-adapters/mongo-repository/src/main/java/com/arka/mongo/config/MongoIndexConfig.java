package com.arka.mongo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final ReactiveMongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        mongoTemplate.indexOps("carts")
                .ensureIndex(new Index()
                        .on("customerId", Sort.Direction.ASC)
                        .on("status", Sort.Direction.ASC)
                        .named("idx_customerId_status"))
                .doOnSuccess(name -> log.info("Ensured index: {} on carts(customerId, status)", name))
                .then(mongoTemplate.indexOps("carts")
                        .ensureIndex(new Index()
                                .on("status", Sort.Direction.ASC)
                                .on("lastModifiedAt", Sort.Direction.ASC)
                                .named("idx_status_lastModifiedAt")))
                .doOnSuccess(name -> log.info("Ensured index: {} on carts(status, lastModifiedAt)", name))
                .subscribe();
    }
}
