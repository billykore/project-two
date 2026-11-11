package org.kore.myapp.adapter.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.entity.TripOffer;
import org.kore.myapp.core.port.TripOfferRepository;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.valueobject.OfferStatus;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kore.myapp.adapter.persistence.PersistenceFixtures.NOW;

@PersistenceAdapterTest
class JpaTripOfferRepositoryIT {

    @Autowired
    private TripOfferRepository offers;

    @Autowired
    private TripRepository trips;

    @Autowired
    private PersistenceFixtures db;

    private UUID tripId;
    private long driverId;

    @BeforeEach
    void setUp() {
        Trip trip = trips.add(db.newTrip(db.insertUser("customer")));
        tripId = trip.getId();
        driverId = db.insertDriver(db.insertUser("driver"));
    }

    @Test
    void findPendingReturnsThePendingOfferOfTheDriver() {
        db.insertOffer(tripId, driverId, OfferStatus.PENDING, NOW.plusSeconds(30));
        db.flushAndClear();

        TripOffer offer = offers.findPending(tripId, driverId).orElseThrow();
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(offer.getExpiresAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void findPendingIgnoresOffersThatAreNoLongerPending() {
        db.insertOffer(tripId, driverId, OfferStatus.ACCEPTED, NOW);
        db.insertOffer(tripId, driverId, OfferStatus.REJECTED, NOW);
        db.insertOffer(tripId, driverId, OfferStatus.EXPIRED, NOW);
        db.flushAndClear();

        assertThat(offers.findPending(tripId, driverId)).isEmpty();
    }

    @Test
    void findPendingIgnoresOtherDrivers() {
        db.insertOffer(tripId, driverId, OfferStatus.PENDING, NOW.plusSeconds(30));
        long otherDriver = db.insertDriver(db.insertUser("other-driver"));
        db.flushAndClear();

        assertThat(offers.findPending(tripId, otherDriver)).isEmpty();
    }

    @Test
    void updatePersistsStatusAndResponseTime() {
        db.insertOffer(tripId, driverId, OfferStatus.PENDING, NOW.plusSeconds(30));
        db.flushAndClear();
        TripOffer offer = offers.findPending(tripId, driverId).orElseThrow();

        offer.accept(NOW);
        offers.update(offer);
        db.flushAndClear();

        TripOffer reloaded = db.find(TripOffer.class, offer.getId());
        assertThat(reloaded.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(reloaded.getRespondedAt()).isEqualTo(NOW);
    }
}
