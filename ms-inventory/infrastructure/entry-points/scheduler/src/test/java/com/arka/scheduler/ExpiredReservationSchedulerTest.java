package com.arka.scheduler;

import com.arka.usecase.stockreservation.StockReservationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiredReservationSchedulerTest {

    @Mock
    private StockReservationUseCase stockReservationUseCase;

    private ExpiredReservationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExpiredReservationScheduler(stockReservationUseCase);
    }

    @Test
    void expireReservations_shouldDelegateToUseCase() {
        when(stockReservationUseCase.expireReservations()).thenReturn(Mono.empty());

        scheduler.expireReservations();

        verify(stockReservationUseCase).expireReservations();
    }

    @Test
    void expireReservations_shouldHandleUseCaseError() {
        when(stockReservationUseCase.expireReservations())
                .thenReturn(Mono.error(new RuntimeException("DB connection failed")));

        scheduler.expireReservations();

        verify(stockReservationUseCase).expireReservations();
    }

    @Test
    void expireReservations_shouldCompleteSuccessfully() {
        when(stockReservationUseCase.expireReservations()).thenReturn(Mono.empty());

        scheduler.expireReservations();

        verify(stockReservationUseCase, times(1)).expireReservations();
        verifyNoMoreInteractions(stockReservationUseCase);
    }
}
