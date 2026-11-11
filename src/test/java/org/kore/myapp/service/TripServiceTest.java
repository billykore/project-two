package org.kore.myapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.exception.InvalidTripException;
import org.kore.myapp.core.port.ServiceTypeRepository;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.CreateTripCommand;
import org.kore.myapp.core.valueobject.Location;
import org.kore.myapp.core.valueobject.PaymentMethod;
import org.kore.myapp.core.valueobject.TripEvent;
import org.kore.myapp.core.valueobject.TripStatus;
import org.kore.myapp.service.support.RecordingEventPublisher;

import java.util.Optional;

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

class TripServiceTest {

    private static final long CUSTOMER_ID = 42;
    private static final ServiceType MY_RIDE = ServiceType.builder().id(1L).name("MyRide").active(true).build();

    private TripRepository tripRepository;
    private ServiceTypeRepository serviceTypeRepository;
    private RecordingEventPublisher events;
    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripRepository = mock(TripRepository.class);
        when(tripRepository.add(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        serviceTypeRepository = mock(ServiceTypeRepository.class);
        when(serviceTypeRepository.findActiveByName("MyRide")).thenReturn(Optional.of(MY_RIDE));
        events = new RecordingEventPublisher();
        tripService = new TripService(
                tripRepository,
                serviceTypeRepository,
                events,
                new FareCalculator(30, 10_000),
                fixedClock(),
                sequentialIds()
        );
    }

    @Test
    void createStoresDistanceAndFareFromCoordinates() {
        Trip trip = tripService.create(command(MONAS, SENAYAN));

        int expectedDistance = MONAS.coordinate().distanceMetersTo(SENAYAN.coordinate());
        assertThat(trip.getDistanceMeters()).isEqualTo(expectedDistance).isPositive();
        assertThat(trip.getFare()).isEqualTo(expectedDistance * 30L);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.REQUESTED);
        assertThat(trip.getServiceTypeId()).isEqualTo(1L);
    }

    @Test
    void createAppliesMinimumFareForShortTrips() {
        Location nearby = new Location(new Coordinate(-6.1755, 106.8272), "Nearby");

        Trip trip = tripService.create(command(MONAS, nearby));

        assertThat(trip.getFare()).isEqualTo(10_000);
    }

    @Test
    void createRejectsSamePickupAndDestination() {
        assertThatThrownBy(() -> tripService.create(command(MONAS, MONAS)))
                .isInstanceOf(InvalidTripException.class);
        verify(tripRepository, never()).add(any());
    }

    @Test
    void createRejectsWhenCustomerHasUnfinishedTrip() {
        Trip requested = Trip.request(id(99), CUSTOMER_ID, MY_RIDE, MONAS, SENAYAN, new FareCalculator(30, 10_000), NOW);
        when(tripRepository.findActiveByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> tripService.create(command(MONAS, SENAYAN)))
                .isInstanceOf(ActiveTripExistsException.class);
        verify(tripRepository, never()).add(any());
    }

    @Test
    void createRejectsUnknownServiceType() {
        CreateTripCommand command = new CreateTripCommand(CUSTOMER_ID, "MYRIDE", MONAS, SENAYAN, PaymentMethod.CASH);

        assertThatThrownBy(() -> tripService.create(command))
                .isInstanceOf(InvalidTripException.class)
                .hasMessage("Unknown service type, expected MyRide or MyCar");
        verify(tripRepository, never()).add(any());
    }

    @Test
    void createChecksServiceTypeBeforeActiveTrip() {
        Trip requested = Trip.request(id(99), CUSTOMER_ID, MY_RIDE, MONAS, SENAYAN, new FareCalculator(30, 10_000), NOW);
        when(tripRepository.findActiveByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(requested));
        CreateTripCommand command = new CreateTripCommand(CUSTOMER_ID, "Bike", MONAS, SENAYAN, PaymentMethod.CASH);

        assertThatThrownBy(() -> tripService.create(command))
                .isInstanceOf(InvalidTripException.class)
                .hasMessage("Unknown service type, expected MyRide or MyCar");
    }

    @Test
    void createUsesInjectedClockAndIds() {
        Trip trip = tripService.create(command(MONAS, SENAYAN));

        assertThat(trip.getId()).isEqualTo(id(1));
        assertThat(trip.getRequestedAt()).isEqualTo(NOW);

        assertThat(events.single(TripEvent.Requested.class)).isEqualTo(new TripEvent.Requested(
                id(2), NOW, id(1), CUSTOMER_ID, 1L, MONAS.coordinate()));
    }

    @Test
    void createPropagatesActiveTripExistsFromRepository() {
        ActiveTripExistsException concurrent = new ActiveTripExistsException(CUSTOMER_ID, null);
        when(tripRepository.add(any(Trip.class))).thenThrow(concurrent);

        assertThatThrownBy(() -> tripService.create(command(MONAS, SENAYAN))).isSameAs(concurrent);
        assertThat(events.events()).isEmpty();
    }

    private static CreateTripCommand command(Location pickup, Location destination) {
        return new CreateTripCommand(CUSTOMER_ID, "MyRide", pickup, destination, PaymentMethod.CASH);
    }
}
