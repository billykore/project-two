package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.TripOffer;
import org.kore.myapp.core.port.TripOfferRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link TripOfferRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaTripOfferRepository implements TripOfferRepository {

    private final SpringDataTripOfferRepository offers;

    JpaTripOfferRepository(SpringDataTripOfferRepository offers) {
        this.offers = offers;
    }

    @Override
    public Optional<TripOffer> findPending(UUID tripId, long driverId) {
        return offers.findPending(tripId, driverId);
    }

    @Override
    public TripOffer update(TripOffer offer) {
        return offers.save(offer);
    }
}
