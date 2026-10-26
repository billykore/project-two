package org.kore.myapp.core.valueobject;

public record Money(long amount) {
    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
    }
}
