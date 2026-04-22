package com.arka.mongo.document;

import com.arka.model.notification.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notification_history")
public class NotificationHistoryDocument {

    @Id
    private UUID id;

    @Indexed(unique = true)
    private String eventId;

    private String eventType;
    private String recipientEmail;
    private String subject;
    private NotificationStatus status;
    private String errorMessage;
    private Instant processedAt;
}
