package com.arka.redis.template;

import com.arka.model.product.Product;
import com.arka.model.valueobjects.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCacheAdapterTest {

    private static final ObjectMapper SHARED_MAPPER;
    
    static {
        SHARED_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .findAndRegisterModules(); // Enable Java records support
        
        // Only serialize fields, ignore getters/methods
        SHARED_MAPPER.setVisibility(SHARED_MAPPER.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE));
    }

    @Mock
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RedisCacheAdapter redisCacheAdapter;
    private ObjectMapper objectMapper;

    private Product testProduct;
    private static final String TEST_KEY = "test-product-id";
    private static final String REDIS_KEY = "product:" + TEST_KEY;

    @BeforeEach
    void setUp() {
        objectMapper = SHARED_MAPPER;
        
        // Constructor expects: ReactiveRedisTemplate, ObjectMapper, ttlSeconds (long)
        redisCacheAdapter = new RedisCacheAdapter(reactiveRedisTemplate, objectMapper, 3600L);

        testProduct = Product.builder()
                .id(UUID.randomUUID())
                .sku("GPU-RTX-4090")
                .name("NVIDIA RTX 4090")
                .description("High-end graphics card")
                .cost(new Money(BigDecimal.valueOf(1200), "USD"))
                .price(new Money(BigDecimal.valueOf(1599), "USD"))
                .categoryId(UUID.randomUUID())
                .active(true)
                .reviews(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void get_shouldReturnProduct_whenCacheHit() throws Exception {
        // Given
        String productJson = objectMapper.writeValueAsString(testProduct);
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(Mono.just(productJson));

        // When & Then
        StepVerifier.create(redisCacheAdapter.get(TEST_KEY))
                .expectNextMatches(product -> 
                        product.sku().equals(testProduct.sku()) &&
                        product.name().equals(testProduct.name()))
                .verifyComplete();

        verify(valueOperations).get(REDIS_KEY);
    }

    @Test
    void get_shouldReturnEmpty_whenCacheMiss() {
        // Given
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(redisCacheAdapter.get(TEST_KEY))
                .verifyComplete();

        verify(valueOperations).get(REDIS_KEY);
    }

    @Test
    void get_shouldReturnEmpty_whenRedisConnectionFails() {
        // Given
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // When & Then
        StepVerifier.create(redisCacheAdapter.get(TEST_KEY))
                .verifyComplete();

        verify(valueOperations).get(REDIS_KEY);
    }

    @Test
    void put_shouldStoreProductWithTTL() throws Exception {
        // Given
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(REDIS_KEY), anyString(), eq(Duration.ofHours(1))))
                .thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(redisCacheAdapter.put(TEST_KEY, testProduct))
                .verifyComplete();

        verify(valueOperations).set(eq(REDIS_KEY), anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    void put_shouldContinue_whenRedisConnectionFails() {
        // Given
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(REDIS_KEY), anyString(), eq(Duration.ofHours(1))))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // When & Then
        StepVerifier.create(redisCacheAdapter.put(TEST_KEY, testProduct))
                .verifyComplete();

        verify(valueOperations).set(eq(REDIS_KEY), anyString(), eq(Duration.ofHours(1)));
    }

    @Test
    void evict_shouldDeleteKey() {
        // Given
        when(reactiveRedisTemplate.delete(REDIS_KEY)).thenReturn(Mono.just(1L));

        // When & Then
        StepVerifier.create(redisCacheAdapter.evict(TEST_KEY))
                .verifyComplete();

        verify(reactiveRedisTemplate).delete(REDIS_KEY);
    }

    @Test
    void evict_shouldContinue_whenRedisConnectionFails() {
        // Given
        when(reactiveRedisTemplate.delete(REDIS_KEY))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // When & Then
        StepVerifier.create(redisCacheAdapter.evict(TEST_KEY))
                .verifyComplete();

        verify(reactiveRedisTemplate).delete(REDIS_KEY);
    }

    @Test
    void evictProductListCache_shouldDeleteAllMatchingKeys() {
        // Given
        when(reactiveRedisTemplate.scan(any()))
                .thenReturn(Flux.just("products:page:0", "products:page:1"));
        when(reactiveRedisTemplate.delete(any(org.reactivestreams.Publisher.class)))
                .thenReturn(Mono.just(2L));

        // When & Then
        StepVerifier.create(redisCacheAdapter.evictProductListCache())
                .verifyComplete();

        verify(reactiveRedisTemplate).scan(any());
        verify(reactiveRedisTemplate).delete(any(org.reactivestreams.Publisher.class));
    }

    @Test
    void evictProductListCache_shouldContinue_whenRedisConnectionFails() {
        // Given
        when(reactiveRedisTemplate.scan(any()))
                .thenReturn(Flux.error(new RuntimeException("Redis connection failed")));
        when(reactiveRedisTemplate.delete(any(org.reactivestreams.Publisher.class)))
                .thenReturn(Mono.just(0L));

        // When & Then
        StepVerifier.create(redisCacheAdapter.evictProductListCache())
                .verifyComplete();

        verify(reactiveRedisTemplate).scan(any());
    }
}
