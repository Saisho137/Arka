package com.arka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    private static final String ROLE_HEADER = "X-User-Role";

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .addFilterBefore(headerAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/shipments/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/shipments/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/shipments/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .build();
    }

    @Bean
    public WebFilter headerAuthenticationFilter() {
        return (exchange, chain) -> {
            String role = exchange.getRequest().getHeaders().getFirst(ROLE_HEADER);
            if (role == null || role.isBlank()) {
                return chain.filter(exchange);
            }
            var authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase().trim());
            var authentication = new UsernamePasswordAuthenticationToken(
                    "gateway-user", null, List.of(authority));
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        };
    }
}
