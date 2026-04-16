package com.arka.model.valueobjects;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Value Object representing monetary amounts with multi-currency support.
 * Supports COP (Colombian Peso), USD (US Dollar), PEN (Peruvian Sol), and CLP (Chilean Peso).
 */
@Builder(toBuilder = true)
public record Money(BigDecimal amount, String currency) {
    
    private static final List<String> SUPPORTED_CURRENCIES = List.of("COP", "USD", "PEN", "CLP");
    
    public Money {
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        Objects.requireNonNull(currency, "Currency is required");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be blank");
        }
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency + 
                ". Supported currencies: " + String.join(", ", SUPPORTED_CURRENCIES));
        }
    }

    /**
     * Compares if this money amount is greater than another.
     * Both amounts must be in the same currency.
     *
     * @param other the money amount to compare with
     * @return true if this amount is greater than the other
     * @throws IllegalArgumentException if currencies don't match
     */
    public boolean isGreaterThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare different currencies: " + 
                this.currency + " vs " + other.currency);
        }
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Checks if the amount is positive (greater than zero).
     *
     * @return true if amount is greater than zero
     */
    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
