package com.arka.model.stockreservation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReservationStatusTest {

    @Test
    @DisplayName("should contain exactly four statuses in expected order")
    void shouldContainAllStatuses() {
        var values = ReservationStatus.values();
        assertEquals(4, values.length);
        assertArrayEquals(
                new ReservationStatus[]{
                        ReservationStatus.PENDING,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.EXPIRED,
                        ReservationStatus.RELEASED
                },
                values
        );
    }

    @Test
    @DisplayName("valueOf should resolve each status from its name")
    void valueOfShouldResolveEachStatus() {
        assertEquals(ReservationStatus.PENDING, ReservationStatus.valueOf("PENDING"));
        assertEquals(ReservationStatus.CONFIRMED, ReservationStatus.valueOf("CONFIRMED"));
        assertEquals(ReservationStatus.EXPIRED, ReservationStatus.valueOf("EXPIRED"));
        assertEquals(ReservationStatus.RELEASED, ReservationStatus.valueOf("RELEASED"));
    }

    @Test
    @DisplayName("valueOf should throw for unknown status")
    void valueOfShouldThrowForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> ReservationStatus.valueOf("UNKNOWN"));
    }
}
