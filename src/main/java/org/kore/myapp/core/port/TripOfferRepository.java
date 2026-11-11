package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.TripOffer;

import java.util.Optional;
import java.util.UUID;

/**
 * Stores and finds {@link TripOffer}s.
 */
public interface TripOfferRepository {

    /**
     * Finds the pending offer of a trip for a specific driver.
     *
     * @param tripId   id of the trip being offered.
     * @param driverId id of the driver who received the offer.
     * @return the {@code PENDING} offer, or empty if there is none.
     */
    Optional<TripOffer> findPending(UUID tripId, long driverId);

    /**
     * Stores changes to an existing offer.
     *
     * @param offer an offer that was loaded from this repository.
     * @return the stored offer.
     */
    TripOffer update(TripOffer offer);
}
