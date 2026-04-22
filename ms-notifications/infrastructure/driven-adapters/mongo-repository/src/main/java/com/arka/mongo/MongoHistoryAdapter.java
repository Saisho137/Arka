package com.arka.mongo;

import com.arka.model.notification.NotificationHistory;
import com.arka.model.notification.gateways.NotificationHistoryRepository;
import com.arka.mongo.document.NotificationHistoryDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MongoHistoryAdapter implements NotificationHistoryRepository {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<NotificationHistory> save(NotificationHistory history) {
        NotificationHistoryDocument doc = toDocument(history);
        return mongoTemplate.save(doc)
                .map(this::toDomain)
                .doOnSuccess(h -> log.debug("NotificationHistory saved: eventId={} status={}",
                        h.eventId(), h.status()))
                .onErrorResume(DuplicateKeyException.class, ex -> {
                    log.debug("NotificationHistory already exists for eventId={} (race condition)",
                            history.eventId());
                    return Mono.just(history);
                });
    }

    @Override
    public Mono<Boolean> existsByEventId(String eventId) {
        Query query = Query.query(Criteria.where("eventId").is(eventId));
        return mongoTemplate.exists(query, NotificationHistoryDocument.class);
    }

    private NotificationHistoryDocument toDocument(NotificationHistory h) {
        return NotificationHistoryDocument.builder()
                .id(h.id())
                .eventId(h.eventId())
                .eventType(h.eventType())
                .recipientEmail(h.recipientEmail())
                .subject(h.subject())
                .status(h.status())
                .errorMessage(h.errorMessage())
                .processedAt(h.processedAt())
                .build();
    }

    private NotificationHistory toDomain(NotificationHistoryDocument doc) {
        return NotificationHistory.builder()
                .id(doc.getId())
                .eventId(doc.getEventId())
                .eventType(doc.getEventType())
                .recipientEmail(doc.getRecipientEmail())
                .subject(doc.getSubject())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .processedAt(doc.getProcessedAt())
                .build();
    }
}
