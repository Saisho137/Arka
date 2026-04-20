package com.arka.config;

import com.arka.model.category.gateways.CategoryRepository;
import com.arka.model.commons.gateways.JsonSerializer;
import com.arka.model.commons.gateways.TransactionalGateway;
import com.arka.model.idempotency.gateways.IdempotencyRepository;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.product.gateways.ProductCachePort;
import com.arka.model.product.gateways.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UseCasesConfigTest {

    @Test
    void testUseCaseBeansExist() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            String[] beanNames = context.getBeanDefinitionNames();

            boolean useCaseBeanFound = false;
            for (String beanName : beanNames) {
                if (beanName.endsWith("UseCase")) {
                    useCaseBeanFound = true;
                    break;
                }
            }

            assertTrue(useCaseBeanFound, "No beans ending with 'UseCase' were found");
        }
    }

    @Configuration
    @Import(UseCasesConfig.class)
    static class TestConfig {

        @Bean
        public ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        public CategoryRepository categoryRepository() {
            return mock(CategoryRepository.class);
        }

        @Bean
        public OutboxEventRepository outboxEventRepository() {
            return mock(OutboxEventRepository.class);
        }

        @Bean
        public ProductCachePort productCachePort() {
            return mock(ProductCachePort.class);
        }

        @Bean
        public IdempotencyRepository idempotencyRepository() {
            return mock(IdempotencyRepository.class);
        }

        @Bean
        public JsonSerializer jsonSerializer() {
            return mock(JsonSerializer.class);
        }

        @Bean
        public TransactionalGateway transactionalGateway() {
            return mock(TransactionalGateway.class);
        }
    }
}
