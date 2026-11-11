package org.kore.myapp.core.valueobject;

/**
 * A non-negative amount of money.
 *
 * @param amount the amount in the smallest currency unit.
 * @throws IllegalArgumentException if {@code amount} is negative.
 */
public record Money(long amount) {
    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
    }
}
