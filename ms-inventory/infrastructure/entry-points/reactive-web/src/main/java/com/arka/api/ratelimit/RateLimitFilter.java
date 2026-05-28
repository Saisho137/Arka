package com.arka.api.ratelimit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter implements WebFilter {

    private static final int DEFAULT_REQUESTS_PER_SECOND = 100;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp,
                k -> new TokenBucket(DEFAULT_REQUESTS_PER_SECOND));

        if (bucket.tryConsume()) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Retry-After", "1");
        return exchange.getResponse().setComplete();
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    private static class TokenBucket {
        private final int maxTokens;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefill;

        TokenBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = new AtomicInteger(maxTokens);
            this.lastRefill = new AtomicLong(System.nanoTime());
        }

        boolean tryConsume() {
            refill();
            return tokens.getAndUpdate(t -> t > 0 ? t - 1 : 0) > 0;
        }

        private void refill() {
            long now = System.nanoTime();
            long last = lastRefill.get();
            long elapsed = now - last;
            if (elapsed >= 1_000_000_000L) {
                if (lastRefill.compareAndSet(last, now)) {
                    tokens.set(maxTokens);
                }
            }
        }
    }
}
