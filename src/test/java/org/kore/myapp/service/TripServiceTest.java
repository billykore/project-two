package org.kore.myapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.common.FareCalculator;
import org.kore.myapp.config.FareProperties;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.exception.InvalidTripException;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.Location;
import org.kore.myapp.core.valueobject.PaymentMethod;
import org.kore.myapp.core.valueobject.ServiceType;
import org.kore.myapp.core.valueobject.TripStatus;
import org.kore.myapp.service.command.CreateTripCommand;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripServiceTest {

    private static final long CUSTOMER_ID = 42;
    private static final Location MONAS = new Location(new Coordinate(-6.1754, 106.8272), "Monas");
    private static final Location SENAYAN = new Location(new Coordinate(-6.2183, 106.8022), "Senayan");

    private TripRepository tripRepository;
    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripRepository = mock(TripRepository.class);
        when(tripRepository.saveAndFlush(any(Trip.class))).thenAnswer(inv -> {
            Trip trip = inv.getArgument(0);
            trip.setId(UUID.randomUUID());
            return trip;
        });
        tripService = new TripService(
                tripRepository,
                mock(ApplicationEventPublisher.class),
                new FareCalculator(new FareProperties(10_000, 30))
        );
    }

    @Test
    void createStoresDistanceAndFareFromCoordinates() {
        Trip trip = tripService.create(command(MONAS, SENAYAN));

        int expectedDistance = MONAS.coordinate().distanceMetersTo(SENAYAN.coordinate());
        assertThat(trip.getDistanceMeters()).isEqualTo(expectedDistance).isPositive();
        assertThat(trip.getFare()).isEqualTo(expectedDistance * 30L);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.REQUESTED);
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
        verify(tripRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsWhenCustomerHasUnfinishedTrip() {
        Trip requested = Trip.builder().id(UUID.randomUUID()).status(TripStatus.REQUESTED).build();
        when(tripRepository.findActiveByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> tripService.create(command(MONAS, SENAYAN)))
                .isInstanceOf(ActiveTripExistsException.class);
        verify(tripRepository, never()).saveAndFlush(any());
    }

    @Test
    void activeTripLookupCoversEveryUnfinishedStatus() {
        when(tripRepository.findActiveByCustomerId(anyLong())).thenCallRealMethod();

        tripRepository.findActiveByCustomerId(CUSTOMER_ID);

        verify(tripRepository).findFirstByCustomerIdAndStatusIn(eq(CUSTOMER_ID), eq(TripStatus.activeStatuses()));
        assertThat(TripStatus.activeStatuses()).containsExactlyInAnyOrder(
                TripStatus.REQUESTED, TripStatus.DRIVER_ASSIGNED, TripStatus.DRIVER_ARRIVED, TripStatus.ON_TRIP);
    }

    private static CreateTripCommand command(Location pickup, Location destination) {
        return new CreateTripCommand(
                CUSTOMER_ID, UUID.randomUUID(), ServiceType.MY_RIDE, pickup, destination, PaymentMethod.CASH);
    }
}
