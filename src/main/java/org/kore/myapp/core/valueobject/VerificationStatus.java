package org.kore.myapp.core.valueobject;

/**
 * Verification lifecycle status.
 *
 * <p>Allowed transitions:
 * <pre>
 * PENDING   -> APPROVED, REJECTED
 * APPROVED  -> SUSPENDED
 * SUSPENDED -> APPROVED
 * REJECTED  -> PENDING
 * </pre>
 */
public enum VerificationStatus {
    /** Awaiting verification review. */
    PENDING,

    /** Verification passed. */
    APPROVED,

    /** Verification failed. */
    REJECTED,

    /** Previously approved, but temporarily blocked. */
    SUSPENDED;

    /**
     * Checks if transition can be made or not.
     *
     * @param next new verification status transition.
     * @return true if transition is permitted.
     */
    public boolean canTransitionTo(VerificationStatus next) {
        return switch (this) {
            case PENDING -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == SUSPENDED;
            case SUSPENDED -> next == APPROVED;
            case REJECTED -> next == PENDING;
        };
    }

    /**
     * Checks if status is allowed to accept trips.
     *
     * @return true if current status is {@link #APPROVED}.
     */
    public boolean canAcceptTrips() {
        return this == APPROVED;
    }
}
