package com.arka.config.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcHealthIndicator implements ReactiveHealthIndicator {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Health> health() {
        return databaseClient.sql("SELECT 1")
                .fetch()
                .first()
                .map(result -> Health.up().withDetail("database", "PostgreSQL reachable").build())
                .onErrorResume(e -> Mono.just(Health.down()
                        .withDetail("database", "PostgreSQL unreachable")
                        .withException(e)
                        .build()));
    }
}
