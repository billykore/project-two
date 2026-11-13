package org.kore.myapp.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.valueobject.TripStatus;

import java.util.Objects;
import java.util.UUID;

public record TripResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "3f8a6c2e-9d41-4f7b-8a3e-0c1d2e3f4a5b") UUID id,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "REQUESTED") TripStatus status,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "1",
                description = "Id of the service type (1 = MyRide, 2 = MyCar in the seed data).") long serviceType
) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(
                Objects.requireNonNull(trip.getId(), "Trip must be persisted"),
                trip.getStatus(),
                trip.getServiceTypeId()
        );
    }
}
