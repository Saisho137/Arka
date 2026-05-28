package com.arka.gateway.mock;

import com.arka.model.payment.PaymentGatewayResult;
import com.arka.model.payment.gateways.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final double SUCCESS_RATE = 0.80;
    private final Random random = new Random();

    @Override
    public Mono<PaymentGatewayResult> charge(UUID orderId, BigDecimal amount, String currency) {
        return Mono.fromCallable(() -> {
            boolean success = random.nextDouble() < SUCCESS_RATE;
            if (success) {
                String transactionId = "mock-txn-" + UUID.randomUUID();
                log.info("Mock gateway: charge approved for orderId={}, amount={} {}, txnId={}",
                        orderId, amount, currency, transactionId);
                return PaymentGatewayResult.builder()
                        .success(true)
                        .transactionId(transactionId)
                        .build();
            } else {
                log.info("Mock gateway: charge rejected for orderId={}, amount={} {}",
                        orderId, amount, currency);
                return PaymentGatewayResult.builder()
                        .success(false)
                        .failureReason("Mock payment rejected (simulated 20% failure rate)")
                        .build();
            }
        });
    }

    @Override
    public String gatewayName() {
        return "MOCK";
    }
}
