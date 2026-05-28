package com.arka.model.stock;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockPropertyTest {

    private Stock createStock(int quantity, int reserved) {
        return Stock.builder()
                .sku("SKU-TEST")
                .productId(UUID.randomUUID())
                .quantity(quantity)
                .reservedQuantity(reserved)
                .depletionThreshold(5)
                .updatedAt(Instant.now())
                .build();
    }

    @Property
    void availableQuantityInvariant(
            @ForAll @IntRange(min = 0, max = 10000) int quantity,
            @ForAll @IntRange(min = 0, max = 10000) int reserved) {
        Assume.that(reserved <= quantity);
        Stock stock = createStock(quantity, reserved);
        assertThat(stock.availableQuantity()).isEqualTo(stock.quantity() - stock.reservedQuantity());
    }

    @Property
    void increaseByPreservesInvariant(
            @ForAll @IntRange(min = 0, max = 5000) int quantity,
            @ForAll @IntRange(min = 0, max = 5000) int reserved,
            @ForAll @IntRange(min = 1, max = 1000) int increase) {
        Assume.that(reserved <= quantity);
        Stock stock = createStock(quantity, reserved);
        Stock result = stock.increaseBy(increase);
        assertThat(result.availableQuantity()).isEqualTo(result.quantity() - result.reservedQuantity());
        assertThat(result.quantity()).isEqualTo(quantity + increase);
        assertThat(result.reservedQuantity()).isEqualTo(reserved);
    }

    @Property
    void decreaseByPreservesInvariant(
            @ForAll @IntRange(min = 1, max = 5000) int quantity,
            @ForAll @IntRange(min = 0, max = 5000) int reserved,
            @ForAll @IntRange(min = 1, max = 5000) int decrease) {
        Assume.that(reserved <= quantity);
        int available = quantity - reserved;
        Assume.that(decrease <= available);
        Stock stock = createStock(quantity, reserved);
        Stock result = stock.decreaseBy(decrease);
        assertThat(result.availableQuantity()).isEqualTo(result.quantity() - result.reservedQuantity());
        assertThat(result.quantity()).isEqualTo(quantity - decrease);
    }

    @Property
    void reserveAndReleaseAreInverse(
            @ForAll @IntRange(min = 1, max = 5000) int quantity,
            @ForAll @IntRange(min = 0, max = 5000) int reserved,
            @ForAll @IntRange(min = 1, max = 5000) int amount) {
        Assume.that(reserved <= quantity);
        int available = quantity - reserved;
        Assume.that(amount <= available);
        Stock stock = createStock(quantity, reserved);
        Stock afterReserve = stock.reserve(amount);
        Stock afterRelease = afterReserve.releaseReservation(amount);
        assertThat(afterRelease.availableQuantity()).isEqualTo(stock.availableQuantity());
        assertThat(afterRelease.quantity()).isEqualTo(stock.quantity());
        assertThat(afterRelease.reservedQuantity()).isEqualTo(stock.reservedQuantity());
    }

    @Property
    void commitReservationReducesBothQuantityAndReserved(
            @ForAll @IntRange(min = 1, max = 5000) int quantity,
            @ForAll @IntRange(min = 1, max = 5000) int reserved,
            @ForAll @IntRange(min = 1, max = 5000) int amount) {
        Assume.that(reserved <= quantity);
        Assume.that(amount <= reserved);
        Assume.that(amount <= quantity);
        Stock stock = createStock(quantity, reserved);
        Stock result = stock.commitReservation(amount);
        assertThat(result.quantity()).isEqualTo(quantity - amount);
        assertThat(result.reservedQuantity()).isEqualTo(reserved - amount);
        assertThat(result.availableQuantity()).isEqualTo(result.quantity() - result.reservedQuantity());
    }

    @Property
    void reserveNeverExceedsAvailable(
            @ForAll @IntRange(min = 0, max = 5000) int quantity,
            @ForAll @IntRange(min = 0, max = 5000) int reserved,
            @ForAll @IntRange(min = 1, max = 5000) int amount) {
        Assume.that(reserved <= quantity);
        int available = quantity - reserved;
        Assume.that(amount <= available);
        Stock stock = createStock(quantity, reserved);
        Stock result = stock.reserve(amount);
        assertThat(result.reservedQuantity()).isLessThanOrEqualTo(result.quantity());
    }
}
