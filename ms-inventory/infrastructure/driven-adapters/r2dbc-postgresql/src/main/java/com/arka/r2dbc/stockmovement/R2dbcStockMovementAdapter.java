package com.arka.r2dbc.stockmovement;

import com.arka.model.stockmovement.StockMovement;
import com.arka.model.stockmovement.gateways.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class R2dbcStockMovementAdapter implements StockMovementRepository {

    private final SpringDataStockMovementRepository repository;

    @Override
    public Mono<StockMovement> save(StockMovement movement) {
        return repository.save(StockMovementDTOMapper.toDTO(movement))
                .map(StockMovementDTOMapper::toDomain);
    }

    @Override
    public Flux<StockMovement> findBySkuOrderByCreatedAtDesc(String sku, int page, int size) {
        return repository.findBySkuPaginated(sku, size, page * size)
                .map(StockMovementDTOMapper::toDomain);
    }
}
