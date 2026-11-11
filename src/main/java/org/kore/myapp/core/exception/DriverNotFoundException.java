package org.kore.myapp.core.exception;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(Long driverUserId) {
        super("Driver not found for userId: " + driverUserId);
    }
}
