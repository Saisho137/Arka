package com.arka.scheduler;

import com.arka.usecase.stockreservation.StockReservationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredReservationScheduler {

    private final StockReservationUseCase stockReservationUseCase;

    @Scheduled(fixedDelay = 60000)
    public void expireReservations() {
        log.info("Starting expired reservations check cycle");
        stockReservationUseCase.expireReservations()
                .doOnSuccess(v -> log.info("Completed expired reservations check cycle"))
                .doOnError(ex -> log.error("Error during expired reservations cycle: {}", ex.getMessage()))
                .subscribe();
    }
}
