package com.arka.mongo.mapper;

import com.arka.model.outboxevent.OutboxEvent;
import com.arka.mongo.document.OutboxEventDocument;

/**
 * Static mapper for converting between OutboxEvent domain model and OutboxEventDocument.
 */
public final class OutboxEventDocumentMapper {
    
    private OutboxEventDocumentMapper() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Converts OutboxEvent domain model to OutboxEventDocument for MongoDB persistence.
     *
     * @param event the domain model
     * @return the MongoDB document
     */
    public static OutboxEventDocument toDocument(OutboxEvent event) {
        if (event == null) {
            return null;
        }
        
        return OutboxEventDocument.builder()
                .id(event.id())
                .eventId(event.id()) // Use same UUID for both id and eventId
                .eventType(event.eventType())
                .payload(event.payload())
                .partitionKey(event.partitionKey())
                .status(event.status())
                .createdAt(event.createdAt())
                .build();
    }
    
    /**
     * Converts OutboxEventDocument from MongoDB to OutboxEvent domain model.
     *
     * @param document the MongoDB document
     * @return the domain model
     */
    public static OutboxEvent toDomain(OutboxEventDocument document) {
        if (document == null) {
            return null;
        }
        
        return OutboxEvent.builder()
                .id(document.getId())
                .eventType(document.getEventType())
                .payload(document.getPayload())
                .partitionKey(document.getPartitionKey())
                .status(document.getStatus())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
