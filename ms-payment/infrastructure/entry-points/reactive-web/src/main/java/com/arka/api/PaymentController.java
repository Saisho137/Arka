package com.arka.api;

import com.arka.model.payment.Payment;
import com.arka.usecase.processpayment.ProcessPaymentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get payment by order ID")
    public Mono<ResponseEntity<Payment>> getPaymentByOrderId(@PathVariable UUID orderId) {
        return processPaymentUseCase.getPaymentByOrderId(orderId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
