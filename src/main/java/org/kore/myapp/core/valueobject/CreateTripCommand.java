package org.kore.myapp.core.valueobject;

import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.InvalidTripException;

/**
 * Data required to request a {@link Trip}.
 *
 * @param customerId      id of the customer requesting the trip.
 * @param serviceTypeName name of the service type ordered, such as {@code MyRide} or {@code MyCar}.
 * @param pickup          place where the customer will be picked up.
 * @param destination     place where the customer wants to go.
 * @param paymentMethod   method the customer will use to pay for the trip.
 * @throws InvalidTripException if any component is missing or the customer id is not positive.
 */
public record CreateTripCommand(
        long customerId,
        String serviceTypeName,
        Location pickup,
        Location destination,
        PaymentMethod paymentMethod
) {
    public CreateTripCommand {
        if (customerId <= 0) {
            throw new InvalidTripException("Customer id must be positive");
        }
        if (serviceTypeName == null || serviceTypeName.isBlank()) {
            throw new InvalidTripException("Service type is required");
        }
        if (pickup == null) {
            throw new InvalidTripException("Pickup is required");
        }
        if (destination == null) {
            throw new InvalidTripException("Destination is required");
        }
        if (paymentMethod == null) {
            throw new InvalidTripException("Payment method is required");
        }
    }
}
