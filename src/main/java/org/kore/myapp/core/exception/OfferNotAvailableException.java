package org.kore.myapp.core.exception;

import java.util.UUID;

public class OfferNotAvailableException extends RuntimeException {
    public OfferNotAvailableException(UUID tripId) {
        super("Offer not available: " + tripId);
    }
}
