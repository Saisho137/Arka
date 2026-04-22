package com.arka.model.notification.gateways;

import com.arka.model.notification.NotificationHistory;
import reactor.core.publisher.Mono;

public interface NotificationHistoryRepository {

    Mono<NotificationHistory> save(NotificationHistory history);

    Mono<Boolean> existsByEventId(String eventId);
}
