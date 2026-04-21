package com.arka.r2dbc.orderstatehistory;

import com.arka.model.order.OrderStateHistory;
import com.arka.model.order.gateways.OrderStateHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class R2dbcOrderStateHistoryAdapter implements OrderStateHistoryRepository {

    private final SpringDataOrderStateHistoryRepository repository;

    @Override
    public Mono<OrderStateHistory> save(OrderStateHistory history) {
        return repository.save(OrderStateHistoryDTOMapper.toDTO(history))
                .map(OrderStateHistoryDTOMapper::toDomain);
    }

    @Override
    public Flux<OrderStateHistory> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId)
                .map(OrderStateHistoryDTOMapper::toDomain);
    }
}
