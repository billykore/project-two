package org.kore.myapp.api.response;

import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.valueobject.TripStatus;

import java.util.Objects;
import java.util.UUID;

public record TripResponse(
        UUID id,
        TripStatus status,
        long serviceType
) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(
                Objects.requireNonNull(trip.getId(), "Trip must be persisted"),
                trip.getStatus(),
                trip.getServiceTypeId()
        );
    }
}
