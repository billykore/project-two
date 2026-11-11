package org.kore.myapp.core.exception;

/**
 * Thrown when a trip request breaks a business rule, such as pickup and destination being the same place.
 */
public class InvalidTripException extends RuntimeException {
    public InvalidTripException(String message) {
        super(message);
    }
}
