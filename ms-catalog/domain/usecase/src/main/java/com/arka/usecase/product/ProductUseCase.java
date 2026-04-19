package com.arka.usecase.product;

import com.arka.model.category.gateways.CategoryRepository;
import com.arka.model.commons.exception.CategoryNotFoundException;
import com.arka.model.commons.exception.DuplicateSkuException;
import com.arka.model.commons.exception.InvalidProductStateException;
import com.arka.model.commons.exception.ProductNotFoundException;
import com.arka.model.commons.gateways.JsonSerializer;
import com.arka.model.commons.gateways.TransactionalGateway;
import com.arka.model.outboxevent.EventType;
import com.arka.model.outboxevent.OutboxEvent;
import com.arka.model.outboxevent.PriceChangedPayload;
import com.arka.model.outboxevent.ProductCreatedPayload;
import com.arka.model.outboxevent.ProductUpdatedPayload;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.product.Product;
import com.arka.model.product.Review;
import com.arka.model.product.gateways.ProductCachePort;
import com.arka.model.product.gateways.ProductRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ProductUseCase {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductCachePort productCachePort;
    private final JsonSerializer jsonSerializer;
    private final TransactionalGateway transactionalGateway;

    public Mono<Product> create(Product product, int initialStock) {
        Mono<Product> pipeline = validateSkuUnique(product.sku())
                .then(validateCategoryExists(product.categoryId()))
                .then(productRepository.save(product))
                .flatMap(savedProduct -> publishProductCreatedEvent(savedProduct, initialStock)
                        .thenReturn(savedProduct));

        return transactionalGateway.executeInTransaction(pipeline)
                .flatMap(savedProduct -> productCachePort.evictProductListCache()
                        .thenReturn(savedProduct));
    }

    public Mono<Product> getById(UUID id) {
        String cacheKey = "product:" + id;

        return productCachePort.get(cacheKey)
                .switchIfEmpty(Mono.defer(() ->
                        productRepository.findById(id)
                                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                                .flatMap(product -> productCachePort.put(cacheKey, product)
                                        .thenReturn(product))
                ));
    }

    public Flux<Product> listActive(int page, int size) {
        return productRepository.findAllActive(page, size);
    }

    public Mono<Product> update(UUID id, Product updatedProduct) {
        Mono<Product> pipeline = productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .flatMap(existingProduct -> {
                    if (!existingProduct.active()) {
                        return Mono.error(new InvalidProductStateException(id, "update", "INACTIVE"));
                    }

                    Product productToUpdate = updatedProduct.toBuilder()
                            .id(id)
                            .sku(existingProduct.sku())
                            .active(existingProduct.active())
                            .reviews(existingProduct.reviews())
                            .createdAt(existingProduct.createdAt())
                            .updatedAt(Instant.now())
                            .build();

                    boolean priceChanged = !existingProduct.price().amount().equals(productToUpdate.price().amount());

                    return productRepository.update(productToUpdate)
                            .flatMap(updated -> publishProductUpdatedEvent(updated)
                                    .then(priceChanged
                                            ? publishPriceChangedEvent(updated, existingProduct.price().amount())
                                            : Mono.empty())
                                    .thenReturn(updated));
                });

        return transactionalGateway.executeInTransaction(pipeline)
                .flatMap(updated -> invalidateProductCache(updated.id()).thenReturn(updated));
    }

    public Mono<Product> deactivate(UUID id) {
        Mono<Product> pipeline = productRepository.findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .flatMap(product -> {
                    if (!product.active()) {
                        return Mono.error(new InvalidProductStateException(id, "deactivate", "INACTIVE"));
                    }

                    return productRepository.deactivate(id)
                            .flatMap(deactivated -> publishProductUpdatedEvent(deactivated)
                                    .thenReturn(deactivated));
                });

        return transactionalGateway.executeInTransaction(pipeline)
                .flatMap(deactivated -> invalidateProductCache(deactivated.id()).thenReturn(deactivated));
    }

    public Mono<Product> addReview(UUID productId, Review review) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(productId)))
                .flatMap(product -> {
                    if (!product.active()) {
                        return Mono.error(new InvalidProductStateException(productId, "add review to", "INACTIVE"));
                    }

                    return productRepository.addReview(productId, review);
                });
    }

    private Mono<Void> validateSkuUnique(String sku) {
        return productRepository.findBySku(sku)
                .flatMap(existing -> Mono.error(new DuplicateSkuException(sku)))
                .then();
    }

    private Mono<Void> validateCategoryExists(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new CategoryNotFoundException(categoryId)))
                .then();
    }

    private Mono<Void> publishProductCreatedEvent(Product product, int initialStock) {
        return Mono.defer(() -> {
            OutboxEvent outboxEvent = buildOutboxEvent(
                    EventType.PRODUCT_CREATED,
                    product.id().toString(),
                    ProductCreatedPayload.builder()
                            .productId(product.id())
                            .sku(product.sku())
                            .name(product.name())
                            .cost(product.cost().amount())
                            .price(product.price().amount())
                            .currency(product.price().currency())
                            .categoryId(product.categoryId())
                            .initialStock(initialStock)
                            .build());

            return outboxEventRepository.save(outboxEvent).then();
        });
    }

    private Mono<Void> publishProductUpdatedEvent(Product product) {
        return Mono.defer(() -> {
            OutboxEvent outboxEvent = buildOutboxEvent(
                    EventType.PRODUCT_UPDATED,
                    product.id().toString(),
                    ProductUpdatedPayload.builder()
                            .productId(product.id())
                            .sku(product.sku())
                            .name(product.name())
                            .cost(product.cost().amount())
                            .price(product.price().amount())
                            .currency(product.price().currency())
                            .categoryId(product.categoryId())
                            .active(product.active())
                            .build());

            return outboxEventRepository.save(outboxEvent).then();
        });
    }

    private Mono<Void> publishPriceChangedEvent(Product product, BigDecimal oldPrice) {
        return Mono.defer(() -> {
            OutboxEvent outboxEvent = buildOutboxEvent(
                    EventType.PRICE_CHANGED,
                    product.id().toString(),
                    PriceChangedPayload.builder()
                            .productId(product.id())
                            .sku(product.sku())
                            .oldPrice(oldPrice)
                            .newPrice(product.price().amount())
                            .currency(product.price().currency())
                            .build());

            return outboxEventRepository.save(outboxEvent).then();
        });
    }

    private Mono<Void> invalidateProductCache(UUID productId) {
        String cacheKey = "product:" + productId;
        return productCachePort.evict(cacheKey)
                .then(productCachePort.evictProductListCache());
    }

    private OutboxEvent buildOutboxEvent(EventType eventType, String partitionKey, Object payload) {
        return OutboxEvent.builder()
                .eventType(eventType)
                .payload(jsonSerializer.toJson(payload))
                .partitionKey(partitionKey)
                .build();
    }
}
