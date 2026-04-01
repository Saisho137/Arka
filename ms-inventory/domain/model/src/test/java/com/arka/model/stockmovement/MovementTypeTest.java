package com.arka.model.stockmovement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementTypeTest {

    @Test
    @DisplayName("should contain exactly six movement types in expected order")
    void shouldContainAllMovementTypes() {
        var values = MovementType.values();
        assertEquals(6, values.length);
        assertArrayEquals(
                new MovementType[]{
                        MovementType.RESTOCK,
                        MovementType.SHRINKAGE,
                        MovementType.ORDER_RESERVE,
                        MovementType.ORDER_CONFIRM,
                        MovementType.RESERVATION_RELEASE,
                        MovementType.PRODUCT_CREATION
                },
                values
        );
    }

    @Test
    @DisplayName("valueOf should resolve each movement type from its name")
    void valueOfShouldResolveEachType() {
        assertEquals(MovementType.RESTOCK, MovementType.valueOf("RESTOCK"));
        assertEquals(MovementType.SHRINKAGE, MovementType.valueOf("SHRINKAGE"));
        assertEquals(MovementType.ORDER_RESERVE, MovementType.valueOf("ORDER_RESERVE"));
        assertEquals(MovementType.ORDER_CONFIRM, MovementType.valueOf("ORDER_CONFIRM"));
        assertEquals(MovementType.RESERVATION_RELEASE, MovementType.valueOf("RESERVATION_RELEASE"));
        assertEquals(MovementType.PRODUCT_CREATION, MovementType.valueOf("PRODUCT_CREATION"));
    }

    @Test
    @DisplayName("valueOf should throw for unknown movement type")
    void valueOfShouldThrowForUnknown() {
        assertThrows(IllegalArgumentException.class, () -> MovementType.valueOf("MANUAL_ADJUSTMENT"));
    }

    @Test
    @DisplayName("MANUAL_ADJUSTMENT should no longer exist as a valid value")
    void manualAdjustmentShouldNotExist() {
        assertThrows(IllegalArgumentException.class, () -> MovementType.valueOf("MANUAL_ADJUSTMENT"));
    }
}
