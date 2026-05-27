package com.arka.model.cart.gateways;

import lombok.Builder;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ProductPriceGateway {

    Mono<ProductInfo> getProductInfo(String sku);

    @Builder
    record ProductInfo(String sku, String name, BigDecimal price) {}
}
