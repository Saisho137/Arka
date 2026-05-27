package com.arka.r2dbc.processedevent;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface SpringDataProcessedEventRepository extends ReactiveCrudRepository<ProcessedEventDTO, UUID> {
}
