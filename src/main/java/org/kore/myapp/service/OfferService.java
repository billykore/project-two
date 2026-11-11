package org.kore.myapp.service;

import lombok.RequiredArgsConstructor;
import org.kore.myapp.core.entity.DriverProfile;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.entity.TripOffer;
import org.kore.myapp.core.exception.DriverNotFoundException;
import org.kore.myapp.core.exception.OfferNotAvailableException;
import org.kore.myapp.core.exception.TripNotFoundException;
import org.kore.myapp.core.port.*;
import org.kore.myapp.core.valueobject.TripEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferService implements TripOfferUseCase {

    private final DriverProfileRepository drivers;

    private final TripOfferRepository offers;

    private final TripRepository trips;

    private final EventPublisher events;

    @Override
    @Transactional
    public Trip accept(UUID tripId, long driverUserId) {
        Instant now = Instant.now();

        DriverProfile driver = drivers.findByUserId(driverUserId)
                .orElseThrow(() -> new DriverNotFoundException(driverUserId));

        TripOffer offer = offers.findPending(tripId, driver.getId())
                .filter(o -> !o.isExpired(now))
                .orElseThrow(() -> new OfferNotAvailableException(tripId));

        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));


        trip.assignDriver(driver);
        trips.save(trip);

        driver.startTrip();
        drivers.save(driver);

        offer.accept(now);
        offers.save(offer);

        events.publish(new TripEvent.DriverAssigned(
                UUID.randomUUID(),
                now,
                trip.getId(),
                driver.getId()
        ));

        return trip;
    }

    @Override
    @Transactional
    public void reject(UUID tripId, long driverUserId) {
        DriverProfile driver = drivers.findByUserId(driverUserId)
                .orElseThrow(() -> new DriverNotFoundException(driverUserId));

        offers.findPending(tripId, driver.getId()).ifPresent(offer -> {
            Instant now = Instant.now();
            offer.reject(now);
            offers.save(offer);
            events.publish(new TripEvent.Rejected(UUID.randomUUID(), now, tripId));
        });
    }
}
