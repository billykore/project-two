package org.kore.myapp.core.valueobject;

/**
 * The party that canceled a trip.
 */
public enum CancelledBy {
    /** Canceled by the customer who requested the trip. */
    CUSTOMER,

    /** Canceled by the assigned driver. */
    DRIVER,

    /** Canceled automatically by the system. */
    SYSTEM,
}
