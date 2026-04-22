package com.arka.mongo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Registers MongoDB transaction manager — requires a replica set (already configured via docker-compose).
 */
@Configuration
public class MongoConfig {

    @Bean
    public ReactiveMongoTransactionManager transactionManager(ReactiveMongoDatabaseFactory dbFactory) {
        return new ReactiveMongoTransactionManager(dbFactory);
    }

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveMongoTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}

