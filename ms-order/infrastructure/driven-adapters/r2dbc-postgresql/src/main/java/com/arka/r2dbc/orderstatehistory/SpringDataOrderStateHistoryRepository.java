package com.arka.r2dbc.orderstatehistory;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SpringDataOrderStateHistoryRepository extends ReactiveCrudRepository<OrderStateHistoryDTO, UUID> {

    Flux<OrderStateHistoryDTO> findByOrderId(UUID orderId);
}
