package com.arka.redis.template;

import com.arka.model.product.Product;
import com.arka.model.product.gateways.ProductCachePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis Cache-Aside adapter for Product caching.
 * Implements resilience by catching connection exceptions and returning empty results.
 * TTL is externalized via 'cache.redis.ttl' (seconds).
 */
@Slf4j
@Component
public class RedisCacheAdapter implements ProductCachePort {

    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String PRODUCT_LIST_KEY_PATTERN = "products:page:*";

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisCacheAdapter(ReactiveRedisTemplate<String, String> reactiveRedisTemplate,
                             ObjectMapper objectMapper,
                             @Value("${cache.redis.ttl:3600}") long ttlSeconds) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Mono<Product> get(String key) {
        String redisKey = PRODUCT_KEY_PREFIX + key;
        
        return reactiveRedisTemplate.opsForValue()
                .get(redisKey)
                .flatMap(this::deserializeProduct)
                .doOnNext(product -> log.debug("Cache HIT for key: {}", redisKey))
                .onErrorResume(ex -> {
                    log.warn("Redis error on get({}): {}, returning empty", key, ex.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> put(String key, Product product) {
        String redisKey = PRODUCT_KEY_PREFIX + key;
        
        return serializeProduct(product)
                .flatMap(json -> reactiveRedisTemplate.opsForValue()
                        .set(redisKey, json, ttl))
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Cache PUT for key: {} with TTL: {}", redisKey, ttl);
                    } else {
                        log.warn("Cache PUT failed for key: {}", redisKey);
                    }
                })
                .then()
                .onErrorResume(ex -> {
                    log.warn("Redis error on put({}): {}, continuing without cache", key, ex.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> evict(String key) {
        String redisKey = PRODUCT_KEY_PREFIX + key;
        
        return reactiveRedisTemplate.delete(redisKey)
                .doOnSuccess(count -> log.debug("Cache EVICT for key: {} (deleted: {})", redisKey, count))
                .then()
                .onErrorResume(ex -> {
                    log.warn("Redis error on evict({}): {}, continuing", key, ex.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> evictProductListCache() {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(PRODUCT_LIST_KEY_PATTERN)
                .count(100)
                .build();

        return reactiveRedisTemplate.delete(reactiveRedisTemplate.scan(scanOptions))
                .doOnSuccess(count -> log.debug("Cache EVICT for pattern: {} (deleted: {})",
                        PRODUCT_LIST_KEY_PATTERN, count))
                .then()
                .onErrorResume(ex -> {
                    log.warn("Redis connection error on evictProductListCache(), continuing: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Serialize Product to JSON string.
     */
    private Mono<String> serializeProduct(Product product) {
        return Mono.fromCallable(() -> {
            try {
                return objectMapper.writeValueAsString(product);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize Product to JSON", e);
            }
        });
    }

    /**
     * Deserialize JSON string to Product.
     */
    private Mono<Product> deserializeProduct(String json) {
        return Mono.fromCallable(() -> {
            try {
                return objectMapper.readValue(json, Product.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize Product from JSON: {}", json, e);
                throw new IllegalStateException("Failed to deserialize Product from JSON", e);
            }
        });
    }
}
