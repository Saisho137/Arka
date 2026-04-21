package com.arka.r2dbc.order;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface SpringDataOrderRepository extends ReactiveCrudRepository<OrderDTO, UUID> {
}
