package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.DriverNotFoundException;
import org.kore.myapp.core.exception.OfferNotAvailableException;
import org.kore.myapp.core.exception.TripNotFoundException;

import java.util.UUID;

/**
 * Business logic for trip offer operations.
 */
public interface TripOfferUseCase {

    /**
     * Accept a pending trip offer on behalf of a driver.
     *
     * @param tripId the id of the {@link Trip} being offered.
     * @param userId the id of the driver accepting the offer.
     * @return the accepted {@link Trip}.
     * @throws DriverNotFoundException     if the user is not a driver.
     * @throws OfferNotAvailableException  if the driver has no pending, unexpired offer for the trip.
     * @throws TripNotFoundException       if the trip does not exist.
     * @throws IllegalStateException       if the trip is no longer waiting for a driver.
     */
    Trip accept(UUID tripId, long userId);

    /**
     * Reject a pending trip offer on behalf of a driver.
     *
     * @param tripId       the id of the {@link Trip} being offered.
     * @param driverUserId the id of the driver rejecting the offer.
     * @throws DriverNotFoundException if the user is not a driver.
     */
    void reject(UUID tripId, long driverUserId);
}
