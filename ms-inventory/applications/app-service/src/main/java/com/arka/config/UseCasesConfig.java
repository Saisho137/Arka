package com.arka.config;

import com.arka.model.commons.gateways.TransactionalGateway;
import com.arka.model.outboxevent.gateways.OutboxEventRepository;
import com.arka.model.processedevent.gateways.ProcessedEventRepository;
import com.arka.model.stock.gateways.StockRepository;
import com.arka.model.stockmovement.gateways.StockMovementRepository;
import com.arka.model.stockreservation.gateways.StockReservationRepository;
import com.arka.usecase.outboxrelay.OutboxRelayUseCase;
import com.arka.usecase.stock.JsonSerializer;
import com.arka.usecase.stock.StockUseCase;
import com.arka.usecase.stockreservation.StockReservationUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCasesConfig {

    @Bean
    public StockUseCase stockUseCase(
            StockRepository stockRepository,
            StockMovementRepository stockMovementRepository,
            OutboxEventRepository outboxEventRepository,
            StockReservationRepository stockReservationRepository,
            ProcessedEventRepository processedEventRepository,
            JsonSerializer jsonSerializer,
            TransactionalGateway transactionalGateway) {
        return new StockUseCase(
                stockRepository,
                stockMovementRepository,
                outboxEventRepository,
                stockReservationRepository,
                processedEventRepository,
                jsonSerializer,
                transactionalGateway);
    }

    @Bean
    public StockReservationUseCase stockReservationUseCase(
            StockReservationRepository stockReservationRepository,
            StockRepository stockRepository,
            StockMovementRepository stockMovementRepository,
            OutboxEventRepository outboxEventRepository,
            ProcessedEventRepository processedEventRepository,
            JsonSerializer jsonSerializer,
            TransactionalGateway transactionalGateway) {
        return new StockReservationUseCase(
                stockReservationRepository,
                stockRepository,
                stockMovementRepository,
                outboxEventRepository,
                processedEventRepository,
                jsonSerializer,
                transactionalGateway);
    }

    @Bean
    public OutboxRelayUseCase outboxRelayUseCase(OutboxEventRepository outboxEventRepository) {
        return new OutboxRelayUseCase(outboxEventRepository);
    }
}
