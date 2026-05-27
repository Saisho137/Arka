package com.arka.catalog;

import com.arka.model.cart.gateways.ProductPriceGateway;
import com.arka.model.commons.exception.CatalogServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Slf4j
@Component
public class WebClientProductPriceAdapter implements ProductPriceGateway {

    private final WebClient webClient;

    public WebClientProductPriceAdapter(
            @Value("${catalog.service.base-url:http://localhost:8081}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Mono<ProductInfo> getProductInfo(String sku) {
        return webClient.get()
                .uri("/api/v1/products/sku/{sku}", sku)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        return Mono.empty();
                    }
                    if (response.statusCode().isError()) {
                        return Mono.error(new CatalogServiceUnavailableException(
                                "Catalog returned status " + response.statusCode().value()));
                    }
                    return response.bodyToMono(CatalogProductResponse.class);
                })
                .map(resp -> ProductInfo.builder()
                        .sku(resp.sku())
                        .name(resp.name())
                        .price(resp.price())
                        .build())
                .onErrorResume(ex -> {
                    if (ex instanceof CatalogServiceUnavailableException) {
                        return Mono.error(ex);
                    }
                    if (ex instanceof java.net.ConnectException
                            || ex.getCause() instanceof java.net.ConnectException) {
                        return Mono.error(new CatalogServiceUnavailableException(ex.getMessage()));
                    }
                    return Mono.error(ex);
                });
    }

    private record CatalogProductResponse(String sku, String name, BigDecimal price) {}
}
