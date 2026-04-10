package com.arka.api.config;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersConfig implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpHeaders headers = exchange.getResponse().getHeaders();

        // Swagger UI and API docs need relaxed CSP to load scripts/styles
        boolean isSwaggerPath = path.startsWith("/swagger-ui")
                || path.startsWith("/webjars/")
                || path.startsWith("/api-docs");

        if (isSwaggerPath) {
            headers.set("Content-Security-Policy",
                    "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;");
        } else {
            headers.set("Content-Security-Policy",
                    "default-src 'self'; frame-ancestors 'self'; form-action 'self'");
        }

        headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Server", "");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");

        return chain.filter(exchange);
    }
}
