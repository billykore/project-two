package org.kore.myapp.core.exception;

import java.util.UUID;

public class TripNotFoundException extends RuntimeException {
    public TripNotFoundException(UUID tripId) {
        super("Trip id " + tripId + " not found");
    }
}
