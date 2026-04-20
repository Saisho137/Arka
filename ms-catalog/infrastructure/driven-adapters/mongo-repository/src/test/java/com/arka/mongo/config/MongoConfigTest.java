package com.arka.mongo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class MongoConfigTest {

    @Mock
    private ReactiveMongoDatabaseFactory dbFactory;

    private final MongoConfig mongoConfig = new MongoConfig();

    @Test
    void transactionManagerBeanIsCreated() {
        ReactiveMongoTransactionManager manager = mongoConfig.transactionManager(dbFactory);
        assertNotNull(manager);
    }

    @Test
    void transactionalOperatorBeanIsCreated() {
        ReactiveMongoTransactionManager manager = mongoConfig.transactionManager(dbFactory);
        TransactionalOperator operator = mongoConfig.transactionalOperator(manager);
        assertNotNull(operator);
    }
}

