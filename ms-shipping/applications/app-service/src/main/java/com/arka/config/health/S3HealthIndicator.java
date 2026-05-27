package com.arka.config.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3HealthIndicator implements ReactiveHealthIndicator {

    private final S3AsyncClient s3AsyncClient;

    @Value("${adapters.aws.s3.bucket-name:arka-shipping-labels}")
    private String bucketName;

    @Override
    public Mono<Health> health() {
        return Mono.fromFuture(() -> s3AsyncClient.headBucket(HeadBucketRequest.builder()
                        .bucket(bucketName)
                        .build()))
                .map(resp -> Health.up().withDetail("s3-bucket", bucketName).build())
                .onErrorResume(e -> {
                    log.warn("S3 health check failed: {}", e.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("s3-bucket", bucketName)
                            .withException(e)
                            .build());
                });
    }
}
