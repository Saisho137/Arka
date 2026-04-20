package com.arka.model.order.gateways;

import com.arka.model.order.ReserveStockResult;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface InventoryClient {

    Mono<ReserveStockResult> reserveStock(String sku, UUID orderId, int quantity);
}
