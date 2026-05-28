package com.arka.model.gateways;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProcessedEventRepository {

    Mono<Boolean> exists(UUID eventId);

    Mono<Void> save(UUID eventId);
}
