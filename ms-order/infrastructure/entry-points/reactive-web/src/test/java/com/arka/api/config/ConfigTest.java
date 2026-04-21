package com.arka.api.config;

import com.arka.api.controller.OrderController;
import com.arka.api.handler.OrderHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {OrderController.class})
@WebFluxTest
@Import({CorsConfig.class, SecurityHeadersConfig.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:4200,http://localhost:8080")
class ConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private OrderHandler orderHandler;

    @Test
    void securityHeadersShouldBePresentOnAllResponses() {
        when(orderHandler.listOrders(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/orders?page=0&size=20")
                .header("X-User-Email", "test@arka.com")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'self'; form-action 'self'")
                .expectHeader().valueEquals("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Server", "")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
    }
}
