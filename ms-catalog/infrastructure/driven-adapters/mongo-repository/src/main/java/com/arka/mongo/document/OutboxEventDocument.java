package com.arka.mongo.document;

import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB document for OutboxEvent.
 * Stores domain events pending publication to Kafka.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox_events")
@CompoundIndex(name = "status_createdAt_idx", def = "{'status': 1, 'createdAt': 1}")
public class OutboxEventDocument {
    
    @Id
    private UUID id;
    
    @Indexed(unique = true)
    private UUID eventId;
    
    private EventType eventType;
    
    private String payload;
    
    private String partitionKey;
    
    private OutboxStatus status;
    
    private Instant createdAt;
}
