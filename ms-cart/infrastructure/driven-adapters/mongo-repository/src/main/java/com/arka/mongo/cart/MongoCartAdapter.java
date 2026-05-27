package com.arka.mongo.cart;

import com.arka.model.cart.Cart;
import com.arka.model.cart.CartItem;
import com.arka.model.cart.CartStatus;
import com.arka.model.cart.gateways.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MongoCartAdapter implements CartRepository {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Cart> save(Cart cart) {
        return mongoTemplate.save(CartMapper.toDocument(cart))
                .map(CartMapper::toDomain);
    }

    @Override
    public Mono<Cart> findById(UUID cartId) {
        return mongoTemplate.findById(cartId, CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Mono<Cart> findByIdAndCustomerId(UUID cartId, String customerId) {
        var query = Query.query(Criteria.where("_id").is(cartId).and("customerId").is(customerId));
        return mongoTemplate.findOne(query, CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Flux<Cart> findByCustomerId(String customerId) {
        var query = Query.query(Criteria.where("customerId").is(customerId))
                .with(Sort.by(Sort.Direction.DESC, "lastModifiedAt"));
        return mongoTemplate.find(query, CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Flux<Cart> findByCustomerIdAndStatus(String customerId, CartStatus status) {
        var query = Query.query(Criteria.where("customerId").is(customerId).and("status").is(status.name()))
                .with(Sort.by(Sort.Direction.DESC, "lastModifiedAt"));
        return mongoTemplate.find(query, CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Mono<Cart> addItem(UUID cartId, CartItem item) {
        var query = Query.query(Criteria.where("_id").is(cartId).and("items.sku").is(item.sku()));
        return mongoTemplate.exists(query, CartDocument.class)
                .flatMap(exists -> {
                    if (exists) {
                        var incQuery = Query.query(Criteria.where("_id").is(cartId).and("items.sku").is(item.sku()));
                        var update = new Update()
                                .inc("items.$.quantity", item.quantity())
                                .set("lastModifiedAt", Instant.now())
                                .set("status", CartStatus.ACTIVE.name());
                        return mongoTemplate.findAndModify(incQuery, update,
                                FindAndModifyOptions.options().returnNew(true), CartDocument.class);
                    } else {
                        var pushQuery = Query.query(Criteria.where("_id").is(cartId));
                        var update = new Update()
                                .push("items", CartItemDocument.fromDomain(item))
                                .set("lastModifiedAt", Instant.now())
                                .set("status", CartStatus.ACTIVE.name());
                        return mongoTemplate.findAndModify(pushQuery, update,
                                FindAndModifyOptions.options().returnNew(true), CartDocument.class);
                    }
                })
                .map(CartMapper::toDomain);
    }

    @Override
    public Mono<Cart> updateItemQuantity(UUID cartId, String sku, int quantity) {
        var query = Query.query(Criteria.where("_id").is(cartId).and("items.sku").is(sku));
        var update = new Update()
                .set("items.$.quantity", quantity)
                .set("lastModifiedAt", Instant.now());
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Mono<Cart> removeItem(UUID cartId, String sku) {
        var query = Query.query(Criteria.where("_id").is(cartId));
        var update = new Update()
                .pull("items", Query.query(Criteria.where("sku").is(sku)))
                .set("lastModifiedAt", Instant.now());
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Mono<Cart> clearItems(UUID cartId) {
        var query = Query.query(Criteria.where("_id").is(cartId));
        var update = new Update()
                .set("items", java.util.List.of())
                .set("lastModifiedAt", Instant.now());
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID cartId) {
        var query = Query.query(Criteria.where("_id").is(cartId));
        return mongoTemplate.remove(query, CartDocument.class).then();
    }

    @Override
    public Mono<Cart> markAsCheckedOut(UUID cartId) {
        var query = Query.query(Criteria.where("_id").is(cartId));
        var update = new Update()
                .set("status", CartStatus.CHECKED_OUT.name())
                .set("lastModifiedAt", Instant.now());
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Flux<Cart> findAbandonedCarts(Instant threshold) {
        var query = Query.query(
                Criteria.where("status").is(CartStatus.ACTIVE.name())
                        .and("lastModifiedAt").lt(threshold)
        );
        return mongoTemplate.find(query, CartDocument.class)
                .map(CartMapper::toDomain);
    }

    @Override
    public Mono<Cart> markAsAbandoned(UUID cartId) {
        var query = Query.query(Criteria.where("_id").is(cartId));
        var update = new Update()
                .set("status", CartStatus.ABANDONED.name())
                .set("lastModifiedAt", Instant.now());
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), CartDocument.class)
                .map(CartMapper::toDomain);
    }
}
