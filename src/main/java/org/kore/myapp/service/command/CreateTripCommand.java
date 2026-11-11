package org.kore.myapp.service.command;

import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.valueobject.Location;
import org.kore.myapp.core.valueobject.PaymentMethod;
import org.kore.myapp.core.valueobject.ServiceType;

import java.util.UUID;

/**
 * Data required to create a {@link Trip}.
 *
 * @param customerId     id of the customer requesting the trip.
 * @param idempotencyKey unique key of the request, used to avoid creating the same trip twice.
 * @param serviceTypeId  service type ordered by the customer.
 * @param pickup         place where the customer will be picked up.
 * @param destination    place where the customer wants to go.
 * @param paymentMethod  method the customer will use to pay for the trip.
 */
public record CreateTripCommand(
        long customerId,

        UUID idempotencyKey,

        ServiceType serviceTypeId,

        Location pickup,

        Location destination,

        PaymentMethod paymentMethod
) {
    /**
     * Straight-line distance from {@link #pickup} to {@link #destination}, in meters.
     */
    public int distanceMeters() {
        return pickup.coordinate().distanceMetersTo(destination.coordinate());
    }
}
