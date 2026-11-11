package org.kore.myapp.core.entity;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.valueobject.TripStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripTest {

    private static final Instant NOW = Instant.parse("2026-11-11T09:00:00Z");

    @Test
    void assignDriverMovesRequestedTripToDriverAssigned() {
        Trip trip = Trip.builder().id(UUID.randomUUID()).status(TripStatus.REQUESTED).build();

        trip.assignDriver(DriverProfile.builder().id(7L).build(), NOW);

        assertThat(trip.getDriverId()).isEqualTo(7L);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
        assertThat(trip.getDriverAssignedAt()).isEqualTo(NOW);
    }

    @Test
    void assignDriverFailsWhenTripIsNotWaitingForADriver() {
        Trip trip = Trip.builder().id(UUID.randomUUID()).status(TripStatus.CANCELLED).build();

        assertThatThrownBy(() -> trip.assignDriver(DriverProfile.builder().id(7L).build(), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(trip.getDriverId()).isNull();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
    }
}
