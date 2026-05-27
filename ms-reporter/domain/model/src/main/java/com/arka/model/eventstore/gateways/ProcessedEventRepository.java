package com.arka.model.eventstore.gateways;

import java.util.UUID;

public interface ProcessedEventRepository {

    boolean exists(UUID eventId);

    void save(UUID eventId);
}
