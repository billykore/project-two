package org.kore.myapp.core.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ActiveTripExistsException extends RuntimeException {

    /** Id of the unfinished trip, or null when it is not known (a concurrent request created it). */
    private final UUID activeTripId;

    public ActiveTripExistsException(long customerId, UUID activeTripId) {
        super("Customer " + customerId + " already has an active trip"
                + (activeTripId != null ? ": " + activeTripId : ""));
        this.activeTripId = activeTripId;
    }
}
