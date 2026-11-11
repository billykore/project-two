package org.kore.myapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.entity.DriverProfile;
import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.entity.TripOffer;
import org.kore.myapp.core.exception.DriverNotFoundException;
import org.kore.myapp.core.exception.OfferNotAvailableException;
import org.kore.myapp.core.exception.TripNotFoundException;
import org.kore.myapp.core.port.DriverProfileRepository;
import org.kore.myapp.core.port.TripOfferRepository;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.valueobject.AvailabilityStatus;
import org.kore.myapp.core.valueobject.OfferStatus;
import org.kore.myapp.core.valueobject.TripEvent;
import org.kore.myapp.core.valueobject.TripStatus;
import org.kore.myapp.service.support.RecordingEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kore.myapp.service.support.Fixtures.MONAS;
import static org.kore.myapp.service.support.Fixtures.NOW;
import static org.kore.myapp.service.support.Fixtures.SENAYAN;
import static org.kore.myapp.service.support.Fixtures.fixedClock;
import static org.kore.myapp.service.support.Fixtures.id;
import static org.kore.myapp.service.support.Fixtures.sequentialIds;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferServiceTest {

    private static final long DRIVER_USER_ID = 70;
    private static final long DRIVER_ID = 7;
    private static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final ServiceType MY_RIDE = ServiceType.builder().id(1L).name("MyRide").active(true).build();

    private DriverProfileRepository drivers;
    private TripOfferRepository offers;
    private TripRepository trips;
    private RecordingEventPublisher events;
    private OfferService offerService;

    private DriverProfile driver;
    private Trip trip;

    @BeforeEach
    void setUp() {
        drivers = mock(DriverProfileRepository.class);
        offers = mock(TripOfferRepository.class);
        trips = mock(TripRepository.class);
        events = new RecordingEventPublisher();
        offerService = new OfferService(drivers, offers, trips, events, fixedClock(), sequentialIds());

        driver = DriverProfile.builder().id(DRIVER_ID).userId(DRIVER_USER_ID)
                .availabilityStatus(AvailabilityStatus.ONLINE).build();
        trip = Trip.request(TRIP_ID, 42, MY_RIDE, MONAS, SENAYAN, new FareCalculator(30, 10_000),
                NOW.minus(Duration.ofMinutes(1)));
        when(drivers.findByUserId(DRIVER_USER_ID)).thenReturn(Optional.of(driver));
        when(trips.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    }

    @Test
    void acceptAssignsDriverAcceptsOfferAndPublishesDriverAssigned() {
        TripOffer offer = pendingOffer(NOW.plusSeconds(30));
        when(offers.findPending(TRIP_ID, DRIVER_ID)).thenReturn(Optional.of(offer));

        Trip accepted = offerService.accept(TRIP_ID, DRIVER_USER_ID);

        assertThat(accepted.getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
        assertThat(accepted.getDriverId()).isEqualTo(DRIVER_ID);
        assertThat(accepted.getDriverAssignedAt()).isEqualTo(NOW);
        assertThat(driver.getAvailabilityStatus()).isEqualTo(AvailabilityStatus.ON_TRIP);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(offer.getRespondedAt()).isEqualTo(NOW);
        verify(trips).update(trip);
        verify(drivers).update(driver);
        verify(offers).update(offer);
        assertThat(events.single(TripEvent.DriverAssigned.class))
                .isEqualTo(new TripEvent.DriverAssigned(id(1), NOW, TRIP_ID, DRIVER_ID));
    }

    @Test
    void acceptFailsForUnknownDriver() {
        when(drivers.findByUserId(DRIVER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerService.accept(TRIP_ID, DRIVER_USER_ID))
                .isInstanceOf(DriverNotFoundException.class);
        assertThat(events.events()).isEmpty();
    }

    @Test
    void acceptFailsWithoutPendingOffer() {
        when(offers.findPending(TRIP_ID, DRIVER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerService.accept(TRIP_ID, DRIVER_USER_ID))
                .isInstanceOf(OfferNotAvailableException.class);
        assertThat(events.events()).isEmpty();
    }

    @Test
    void acceptFailsForExpiredOffer() {
        when(offers.findPending(TRIP_ID, DRIVER_ID)).thenReturn(Optional.of(pendingOffer(NOW.minusSeconds(1))));

        assertThatThrownBy(() -> offerService.accept(TRIP_ID, DRIVER_USER_ID))
                .isInstanceOf(OfferNotAvailableException.class);
        verify(trips, never()).update(any());
    }

    @Test
    void acceptSucceedsForOfferExpiringExactlyNow() {
        when(offers.findPending(TRIP_ID, DRIVER_ID)).thenReturn(Optional.of(pendingOffer(NOW)));

        assertThat(offerService.accept(TRIP_ID, DRIVER_USER_ID).getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
    }

    @Test
    void acceptFailsForMissingTrip() {
        when(offers.findPending(TRIP_ID, DRIVER_ID)).thenReturn(Optional.of(pendingOffer(NOW.plusSeconds(30))));
        when(trips.findById(TRIP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerService.accept(TRIP_ID, DRIVER_USER_ID))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void acceptFailsWhenTripIsNoLongerWaitingForADriver() {
        trip.assignDriver(DriverProfile.builder().id(8L).build(), NOW);
        when(offers.findPending(TRIP_ID, DRIVER_ID)).thenReturn(Optional.of(pendingOffer(NOW.plusSeconds(30))));

        assertThatThrownBy(() -> offerService.accept(TRIP_ID, DRIVER_USER_ID))
                .isInstanceOf(IllegalStateException.class);
        assertThat(events.events()).isEmpty();
    }

    @Test
    void rejectMarksPendingOfferRejectedAndPublishesRejected() {
        TripOffer offer = pendingOffer(NOW.plusSeconds(30));
        when(offers.findPending(TRIP_ID, DRIVER_ID)).thenReturn(Optional.of(offer));

        offerService.reject(TRIP_ID, DRIVER_USER_ID);

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(offer.getRespondedAt()).isEqualTo(NOW);
        verify(offers).update(offer);
        assertThat(events.single(TripEvent.Rejected.class)).isEqualTo(new TripEvent.Rejected(id(1), NOW, TRIP_ID));
    }

    @Test
    void rejectWithoutPendingOfferPublishesNothing() {
        when(offers.findPending(TRIP_ID, DRIVER_ID)).thenReturn(Optional.empty());

        offerService.reject(TRIP_ID, DRIVER_USER_ID);

        assertThat(events.events()).isEmpty();
    }

    private static TripOffer pendingOffer(Instant expiresAt) {
        return TripOffer.builder().id(1L).tripId(TRIP_ID).driverId(DRIVER_ID)
                .status(OfferStatus.PENDING).expiresAt(expiresAt).build();
    }
}
