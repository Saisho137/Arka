package com.arka.scheduler;

import com.arka.usecase.cart.CartUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonmentScheduler {

    private final CartUseCase cartUseCase;

    @Value("${cart.abandonment.threshold:30m}")
    private Duration threshold;

    @Scheduled(fixedDelayString = "${cart.abandonment.check-interval}")
    public void detectAbandonedCarts() {
        log.info("Starting abandoned cart detection with threshold: {}", threshold);
        cartUseCase.detectAbandonedCarts(threshold)
                .doOnNext(count -> log.info("Abandoned cart detection completed. Carts processed: {}", count))
                .doOnError(e -> log.error("Abandoned cart detection failed: {}", e.getMessage()))
                .subscribe();
    }
}
