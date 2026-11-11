package org.kore.myapp.core.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ActiveTripExistsException extends RuntimeException {

    private final UUID activeTripId;

    public ActiveTripExistsException(long customerId, UUID activeTripId) {
        super("Customer " + customerId + " already has an active trip: " + activeTripId);
        this.activeTripId = activeTripId;
    }
}
