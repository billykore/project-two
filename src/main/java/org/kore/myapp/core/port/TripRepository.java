package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.valueobject.TripStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores and finds {@link Trip}s.
 */
public interface TripRepository {

    /**
     * @param id id of the trip.
     * @return the trip, or empty if there is none with that id.
     */
    Optional<Trip> findById(UUID id);

    /**
     * Finds the customer's unfinished trip, if any.
     *
     * @param customerId id of the customer.
     * @return a trip of the customer in one of {@link TripStatus#activeStatuses()}, or empty.
     */
    Optional<Trip> findActiveByCustomerId(long customerId);

    /**
     * Stores a new trip, making the insert visible to database constraints immediately.
     *
     * @param trip a trip that has not been stored before.
     * @return the stored trip.
     * @throws ActiveTripExistsException if a concurrent request created an unfinished trip for the same customer.
     */
    Trip add(Trip trip);

    /**
     * Stores changes to an existing trip.
     *
     * @param trip a trip that was loaded from this repository.
     * @return the stored trip.
     */
    Trip update(Trip trip);
}
