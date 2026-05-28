package com.arka.r2dbc.processedevent;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface SpringDataProcessedEventRepository extends R2dbcRepository<ProcessedEventDTO, UUID> {
}
