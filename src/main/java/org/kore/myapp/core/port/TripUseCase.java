package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.exception.InvalidTripException;
import org.kore.myapp.core.valueobject.CreateTripCommand;

/**
 * Core business logic for manage {@link Trip}.
 */
public interface TripUseCase {
    /**
     * Creates a {@link Trip}.
     *
     * @param command required data to creates a {@link Trip}.
     * @return a created {@link Trip}.
     * @throws InvalidTripException      if the service type is unknown or pickup and destination are the same place.
     * @throws ActiveTripExistsException if the customer already has an active trip.
     */
    Trip create(CreateTripCommand command);
}
