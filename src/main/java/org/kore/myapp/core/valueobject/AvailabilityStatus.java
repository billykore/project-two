package org.kore.myapp.core.valueobject;

/**
 * Availability status of a driver for receiving trips.
 *
 * <p>Allowed transitions:
 * <pre>
 * OFFLINE -> ONLINE
 * ONLINE  -> OFFLINE, ON_TRIP
 * ON_TRIP -> ONLINE
 * </pre>
 */
public enum AvailabilityStatus {
    /** Not accepting trip. */
    OFFLINE,

    /** Ready to accepting trip. */
    ONLINE,

    /** On running trip. */
    ON_TRIP;

    /**
     * Checks if transition can be made or not.
     *
     * @param next new available status transition.
     * @return true if transition is permitted.
     */
    public boolean canTransitionTo(AvailabilityStatus next) {
        return switch (this) {
            case OFFLINE, ON_TRIP -> next == ONLINE;
            case ONLINE -> next == OFFLINE || next == ON_TRIP;
        };
    }

    /**
     * Checks if status is available for assigment.
     *
     * @return true if current status is available for assigment.
     */
    public boolean isAvailableForAssignment() {
        return this == ONLINE;
    }
}
