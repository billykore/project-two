package org.kore.myapp.core.valueobject;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of a trip.
 *
 * <p>A trip starts as {@link #REQUESTED} and ends as either {@link #COMPLETED} or {@link #CANCELLED}.
 * Every status other than {@link #REQUESTED} and {@link #CANCELLED} requires a driver to be assigned.
 */
public enum TripStatus {
    /** Created by the customer, waiting for a driver. */
    REQUESTED,

    /** A driver accepted the trip and is heading to the pickup point. */
    DRIVER_ASSIGNED,

    /** The driver arrived at the pickup point. */
    DRIVER_ARRIVED,

    /** The customer is picked up and the trip is in progress. */
    ON_TRIP,

    /** The customer reached the destination. */
    COMPLETED,

    /** The trip was cancelled, see {@link CancelledBy} for who cancelled it. */
    CANCELLED;

    /**
     * Statuses of a trip that has not ended yet. A customer can have at most one trip in these statuses.
     */
    public static Set<TripStatus> activeStatuses() {
        return EnumSet.of(REQUESTED, DRIVER_ASSIGNED, DRIVER_ARRIVED, ON_TRIP);
    }
}
