package com.arka.model.payment.gateways;

import com.arka.model.payment.PaymentGatewayResult;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {
    Mono<PaymentGatewayResult> charge(UUID orderId, BigDecimal amount, String currency);
    String gatewayName();
}
