package com.arka.model.payment;

import lombok.Builder;

@Builder(toBuilder = true)
public record PaymentGatewayResult(
        boolean success,
        String transactionId,
        String failureReason
) {}
