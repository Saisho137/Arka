package com.arka.config;

import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.stock.gateways.StockRepository;
import com.arka.model.stockmovement.gateways.StockMovementRepository;
import com.arka.model.stockreservation.gateways.StockReservationRepository;
import com.arka.usecase.stock.JsonSerializer;
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
        public StockRepository stockRepository() {
            return mock(StockRepository.class);
        }

        @Bean
        public StockMovementRepository stockMovementRepository() {
            return mock(StockMovementRepository.class);
        }

        @Bean
        public OutboxEventRepository outboxEventRepository() {
            return mock(OutboxEventRepository.class);
        }

        @Bean
        public StockReservationRepository stockReservationRepository() {
            return mock(StockReservationRepository.class);
        }

        @Bean
        public ProcessedEventRepository processedEventRepository() {
            return mock(ProcessedEventRepository.class);
        }

        @Bean
        public JsonSerializer jsonSerializer() {
            return mock(JsonSerializer.class);
        }
    }
}
