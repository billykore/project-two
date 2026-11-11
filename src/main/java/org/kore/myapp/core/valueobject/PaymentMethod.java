package org.kore.myapp.core.valueobject;

/**
 * Method a customer uses to pay for a trip.
 */
public enum PaymentMethod {
    /** Paid in cash. */
    CASH,

    /** Paid by credit card. */
    CREDIT_CARD,

    /** Paid through QRIS. */
    QRIS,
}
