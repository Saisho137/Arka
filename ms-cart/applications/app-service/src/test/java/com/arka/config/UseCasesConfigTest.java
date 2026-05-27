package com.arka.config;

import com.arka.model.cart.gateways.CartEventPublisher;
import com.arka.model.cart.gateways.CartRepository;
import com.arka.model.cart.gateways.ProductPriceGateway;
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
        public CartRepository cartRepository() {
            return mock(CartRepository.class);
        }

        @Bean
        public ProductPriceGateway productPriceGateway() {
            return mock(ProductPriceGateway.class);
        }

        @Bean
        public CartEventPublisher cartEventPublisher() {
            return mock(CartEventPublisher.class);
        }
    }
}
