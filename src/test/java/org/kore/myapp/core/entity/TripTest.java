package org.kore.myapp.core.entity;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.exception.InvalidTripException;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.Location;
import org.kore.myapp.core.valueobject.TripStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripTest {

    private static final Instant NOW = Instant.parse("2026-11-11T09:00:00Z");
    private static final UUID TRIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Location MONAS = new Location(new Coordinate(-6.1754, 106.8272), "Monas");
    private static final Location SENAYAN = new Location(new Coordinate(-6.2183, 106.8022), "Senayan");
    private static final ServiceType MY_RIDE = ServiceType.builder().id(1L).name("MyRide").build();
    private static final FareCalculator FARES = new FareCalculator(30, 10_000);

    @Test
    void requestStartsTheTripAsRequestedWithTheGivenIdAndTime() {
        Trip trip = Trip.request(TRIP_ID, 42, MY_RIDE, MONAS, SENAYAN, FARES, NOW);

        assertThat(trip.getId()).isEqualTo(TRIP_ID);
        assertThat(trip.getCustomerId()).isEqualTo(42);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.REQUESTED);
        assertThat(trip.getRequestedAt()).isEqualTo(NOW);
        assertThat(trip.getDriverId()).isNull();
    }

    @Test
    void requestStoresDistanceAndFareFromCoordinates() {
        Trip trip = Trip.request(TRIP_ID, 42, MY_RIDE, MONAS, SENAYAN, FARES, NOW);

        int expectedDistance = MONAS.coordinate().distanceMetersTo(SENAYAN.coordinate());
        assertThat(trip.getDistanceMeters()).isEqualTo(expectedDistance).isPositive();
        assertThat(trip.getFare()).isEqualTo(expectedDistance * 30L);
        assertThat(trip.getPickupAddress()).isEqualTo("Monas");
        assertThat(trip.getDestinationLat()).isEqualTo(-6.2183);
    }

    @Test
    void requestAppliesTheMinimumFareForShortTrips() {
        Location nearby = new Location(new Coordinate(-6.1755, 106.8272), "Nearby");

        Trip trip = Trip.request(TRIP_ID, 42, MY_RIDE, MONAS, nearby, FARES, NOW);

        assertThat(trip.getFare()).isEqualTo(10_000);
    }

    @Test
    void requestRejectsSamePickupAndDestination() {
        assertThatThrownBy(() -> Trip.request(TRIP_ID, 42, MY_RIDE, MONAS, MONAS, FARES, NOW))
                .isInstanceOf(InvalidTripException.class)
                .hasMessage("Pickup and destination must be different places");
    }

    @Test
    void requestRecordsTheServiceTypeId() {
        ServiceType myCar = ServiceType.builder().id(2L).name("MyCar").build();

        Trip trip = Trip.request(TRIP_ID, 42, myCar, MONAS, SENAYAN, FARES, NOW);

        assertThat(trip.getServiceTypeId()).isEqualTo(2L);
    }

    @Test
    void assignDriverMovesRequestedTripToDriverAssigned() {
        Trip trip = Trip.request(TRIP_ID, 42, MY_RIDE, MONAS, SENAYAN, FARES, NOW);

        trip.assignDriver(DriverProfile.builder().id(7L).build(), NOW);

        assertThat(trip.getDriverId()).isEqualTo(7L);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
        assertThat(trip.getDriverAssignedAt()).isEqualTo(NOW);
    }

    @Test
    void assignDriverFailsWhenTripIsNotWaitingForADriver() {
        Trip trip = Trip.builder().id(TRIP_ID).status(TripStatus.CANCELLED).build();

        assertThatThrownBy(() -> trip.assignDriver(DriverProfile.builder().id(7L).build(), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(trip.getDriverId()).isNull();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
    }
}
