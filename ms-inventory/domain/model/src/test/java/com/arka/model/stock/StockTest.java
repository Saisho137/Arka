package com.arka.model.stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StockTest {

    private static final String SKU = "KB-MX-001";
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Nested
    @DisplayName("Compact constructor validations")
    class Validations {

        @Test
        @DisplayName("should reject null sku")
        void shouldRejectNullSku() {
            var ex = assertThrows(NullPointerException.class, () ->
                    Stock.builder().sku(null).productId(PRODUCT_ID).quantity(10).reservedQuantity(0).build());
            assertEquals("sku is required", ex.getMessage());
        }

        @Test
        @DisplayName("should reject null productId")
        void shouldRejectNullProductId() {
            var ex = assertThrows(NullPointerException.class, () ->
                    Stock.builder().sku(SKU).productId(null).quantity(10).reservedQuantity(0).build());
            assertEquals("productId is required", ex.getMessage());
        }

        @Test
        @DisplayName("should reject negative quantity")
        void shouldRejectNegativeQuantity() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    Stock.builder().sku(SKU).productId(PRODUCT_ID).quantity(-1).reservedQuantity(0).build());
            assertEquals("quantity must be >= 0", ex.getMessage());
        }

        @Test
        @DisplayName("should reject negative reservedQuantity")
        void shouldRejectNegativeReservedQuantity() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    Stock.builder().sku(SKU).productId(PRODUCT_ID).quantity(10).reservedQuantity(-1).build());
            assertEquals("reservedQuantity must be >= 0", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Computed fields")
    class ComputedFields {

        @Test
        @DisplayName("availableQuantity should equal quantity minus reservedQuantity")
        void shouldComputeAvailableQuantity() {
            var stock = Stock.builder().sku(SKU).productId(PRODUCT_ID).quantity(50).reservedQuantity(15).build();
            assertEquals(35, stock.availableQuantity());
        }

        @Test
        @DisplayName("availableQuantity should be zero when fully reserved")
        void shouldComputeZeroAvailableWhenFullyReserved() {
            var stock = Stock.builder().sku(SKU).productId(PRODUCT_ID).quantity(20).reservedQuantity(20).build();
            assertEquals(0, stock.availableQuantity());
        }

        @Test
        @DisplayName("version should default to 1 when not provided or zero")
        void shouldDefaultVersionToOne() {
            var stock = Stock.builder().sku(SKU).productId(PRODUCT_ID).quantity(10).reservedQuantity(0).build();
            assertEquals(1, stock.version());
        }

        @Test
        @DisplayName("version should default to 1 when explicitly set to zero")
        void shouldDefaultVersionToOneWhenZero() {
            var stock = Stock.builder().sku(SKU).productId(PRODUCT_ID).quantity(10).reservedQuantity(0).version(0).build();
            assertEquals(1, stock.version());
        }

        @Test
        @DisplayName("version should preserve explicit positive value")
        void shouldPreserveExplicitVersion() {
            var stock = Stock.builder().sku(SKU).productId(PRODUCT_ID).quantity(10).reservedQuantity(0).version(5).build();
            assertEquals(5, stock.version());
        }
    }

    @Nested
    @DisplayName("Builder and toBuilder")
    class BuilderTests {

        @Test
        @DisplayName("should build with all fields")
        void shouldBuildWithAllFields() {
            var id = UUID.randomUUID();
            var now = Instant.now();

            var stock = Stock.builder()
                    .id(id)
                    .sku(SKU)
                    .productId(PRODUCT_ID)
                    .quantity(100)
                    .reservedQuantity(30)
                    .updatedAt(now)
                    .version(3)
                    .build();

            assertEquals(id, stock.id());
            assertEquals(SKU, stock.sku());
            assertEquals(PRODUCT_ID, stock.productId());
            assertEquals(100, stock.quantity());
            assertEquals(30, stock.reservedQuantity());
            assertEquals(70, stock.availableQuantity());
            assertEquals(now, stock.updatedAt());
            assertEquals(3, stock.version());
        }

        @Test
        @DisplayName("toBuilder should create copy and recompute availableQuantity")
        void toBuilderShouldRecompute() {
            var original = Stock.builder().sku(SKU).productId(PRODUCT_ID).quantity(100).reservedQuantity(10).version(1).build();
            var updated = original.toBuilder().quantity(80).build();

            assertEquals(80, updated.quantity());
            assertEquals(10, updated.reservedQuantity());
            assertEquals(70, updated.availableQuantity());
        }
    }
}
