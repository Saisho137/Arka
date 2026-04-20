package com.arka.mongo;

import com.arka.model.idempotency.gateways.IdempotencyRepository;
import com.arka.mongo.document.IdempotencyDocument;
import com.arka.mongo.mapper.IdempotencyDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoIdempotencyAdapter implements IdempotencyRepository {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Boolean> exists(UUID idempotencyKey) {
        log.debug("Checking idempotency key: {}", idempotencyKey);

        Query query = Query.query(Criteria.where("_id").is(idempotencyKey));

        return mongoTemplate.exists(query, IdempotencyDocument.class)
                .doOnSuccess(exists -> log.debug("Idempotency key {} exists: {}", idempotencyKey, exists));
    }

    @Override
    public Mono<Void> save(UUID idempotencyKey) {
        log.debug("Saving idempotency key: {}", idempotencyKey);

        IdempotencyDocument document = IdempotencyDocumentMapper.toDocument(idempotencyKey);

        return mongoTemplate.save(document)
                .doOnSuccess(d -> log.debug("Idempotency key saved: {}", idempotencyKey))
                .then();
    }
}
