package org.kore.myapp.core.valueobject;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events about the lifecycle of a trip.
 *
 * <p>The {@link #aggregateId()} of every trip event is the {@link #tripId()}.
 */
public sealed interface TripEvent extends Event {
    /**
     * Id of the trip this event is about.
     */
    UUID tripId();

    @Override
    default String aggregateId() {
        return tripId().toString();
    }

    /**
     * A customer requested a new trip.
     *
     * @param customerId    id of the customer who requested the trip.
     * @param serviceTypeId id of the service type ordered.
     * @param pickup        coordinate of the pickup point.
     */
    record Requested(
            UUID eventId,
            Instant occurredAt,
            UUID tripId,
            long customerId,
            long serviceTypeId,
            Coordinate pickup
    ) implements TripEvent {
    }

    record Rejected(
            UUID eventId,
            Instant occurredAt,
            UUID tripId
    ) implements TripEvent {
    }

    /**
     * A driver was assigned to the trip.
     *
     * @param driverId id of the assigned driver.
     */
    record DriverAssigned(
            UUID eventId,
            Instant occurredAt,
            UUID tripId,
            long driverId
    ) implements TripEvent {
    }

    /**
     * The trip was completed.
     *
     * @param fare final fare of the trip.
     */
    record Completed(
            UUID eventId,
            Instant occurredAt,
            UUID tripId,
            Money fare
    ) implements TripEvent {
    }

    /**
     * The trip was cancelled.
     *
     * @param cancelledBy the party that cancelled the trip.
     */
    record Cancelled(
            UUID eventId,
            Instant occurredAt,
            UUID tripId,
            CancelledBy cancelledBy
    ) implements TripEvent {
    }
}
