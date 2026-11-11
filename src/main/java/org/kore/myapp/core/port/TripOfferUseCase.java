package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.Trip;

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
     */
    Trip accept(UUID tripId, long userId);

    /**
     * Reject a pending trip offer on behalf of a driver.
     *
     * @param tripId       the id of the {@link Trip} being offered.
     * @param driverUserId the id of the driver rejecting the offer.
     */
    void reject(UUID tripId, long driverUserId);
}
