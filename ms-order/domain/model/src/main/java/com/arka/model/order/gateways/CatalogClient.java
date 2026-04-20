package com.arka.model.order.gateways;

import com.arka.model.order.ProductInfo;
import reactor.core.publisher.Mono;

public interface CatalogClient {

    Mono<ProductInfo> getProductInfo(String sku);
}
