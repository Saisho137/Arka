package com.arka.config;

import com.arka.model.order.gateways.OrderItemRepository;
import com.arka.model.order.gateways.OrderRepository;
import com.arka.model.order.gateways.OrderStateHistoryRepository;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.order.gateways.InventoryClient;
import com.arka.model.order.gateways.CatalogClient;
import com.arka.model.commons.gateways.TransactionalGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UseCasesConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UseCasesConfig.class, MockRepositoriesConfig.class);

    @Test
    void testUseCaseBeansExist() {
        contextRunner.run(context -> {
            String[] beanNames = context.getBeanDefinitionNames();

            boolean useCaseBeanFound = false;
            for (String beanName : beanNames) {
                if (beanName.endsWith("UseCase")) {
                    useCaseBeanFound = true;
                    break;
                }
            }

            assertThat(useCaseBeanFound)
                    .as("At least one bean ending with 'UseCase' should be found")
                    .isTrue();
        });
    }

    @Configuration
    static class MockRepositoriesConfig {

        @Bean
        public OrderRepository orderRepository() {
            return mock(OrderRepository.class);
        }

        @Bean
        public OrderItemRepository orderItemRepository() {
            return mock(OrderItemRepository.class);
        }

        @Bean
        public OrderStateHistoryRepository orderStateHistoryRepository() {
            return mock(OrderStateHistoryRepository.class);
        }

        @Bean
        public OutboxEventRepository outboxEventRepository() {
            return mock(OutboxEventRepository.class);
        }

        @Bean
        public ProcessedEventRepository processedEventRepository() {
            return mock(ProcessedEventRepository.class);
        }

        @Bean
        public InventoryClient inventoryClient() {
            return mock(InventoryClient.class);
        }

        @Bean
        public CatalogClient catalogClient() {
            return mock(CatalogClient.class);
        }

        @Bean
        public TransactionalGateway transactionalGateway() {
            return mock(TransactionalGateway.class);
        }

        @Bean
        public com.arka.usecase.order.JsonSerializer jsonSerializer() {
            return mock(com.arka.usecase.order.JsonSerializer.class);
        }
    }
}
