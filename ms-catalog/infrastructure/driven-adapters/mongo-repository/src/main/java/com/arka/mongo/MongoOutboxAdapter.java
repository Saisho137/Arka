package com.arka.mongo;

import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.OutboxStatus;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.mongo.document.OutboxEventDocument;
import com.arka.mongo.mapper.OutboxEventDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
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
 * MongoDB adapter implementing OutboxEventRepository port.
 * Manages outbox events for reliable event publishing with Outbox Pattern.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoOutboxAdapter implements OutboxEventRepository {
    
    private final ReactiveMongoTemplate mongoTemplate;
    
    @Override
    public Mono<OutboxEvent> save(OutboxEvent event) {
        log.debug("Saving outbox event with type: {}", event.eventType());
        
        OutboxEventDocument document = OutboxEventDocumentMapper.toDocument(event);
        
        // Generate UUID if not present
        if (document.getId() == null) {
            document.setId(UUID.randomUUID());
            document.setEventId(document.getId());
        }
        
        // Set createdAt if not present
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(Instant.now());
        }
        
        return mongoTemplate.save(document)
                .map(OutboxEventDocumentMapper::toDomain)
                .doOnSuccess(e -> log.debug("Outbox event saved with ID: {}", e.id()));
    }
    
    @Override
    public Flux<OutboxEvent> findPending(int limit) {
        log.debug("Finding pending outbox events with limit: {}", limit);
        
        Query query = Query.query(Criteria.where("status").is(OutboxStatus.PENDING))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"))
                .limit(limit);
        
        return mongoTemplate.find(query, OutboxEventDocument.class)
                .map(OutboxEventDocumentMapper::toDomain)
                .doOnComplete(() -> log.debug("Completed finding pending outbox events"));
    }
    
    @Override
    public Mono<Void> markAsPublished(UUID eventId) {
        log.debug("Marking outbox event as published with ID: {}", eventId);
        
        Query query = Query.query(Criteria.where("_id").is(eventId));
        Update update = new Update().set("status", OutboxStatus.PUBLISHED);
        
        return mongoTemplate.updateFirst(query, update, OutboxEventDocument.class)
                .doOnSuccess(result -> {
                    if (result.getModifiedCount() > 0) {
                        log.debug("Outbox event marked as published with ID: {}", eventId);
                    } else {
                        log.warn("Outbox event not found or already published with ID: {}", eventId);
                    }
                })
                .then();
    }
}
