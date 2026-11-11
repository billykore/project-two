package org.kore.myapp.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.exception.InvalidTripException;
import org.kore.myapp.core.valueobject.CancelledBy;
import org.kore.myapp.core.valueobject.Location;
import org.kore.myapp.core.valueobject.TripStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@Builder(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Trip {

    /** Assigned by {@link #request}, from an {@code IdGenerator}, because it appears in events. */
    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private long customerId;

    /** Null until a driver is found. */
    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "service_type_id", nullable = false, updatable = false)
    private long serviceTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TripStatus status = TripStatus.REQUESTED;

    @Column(name = "pickup_lat", nullable = false, updatable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false, updatable = false)
    private double pickupLng;

    @Column(name = "pickup_address", length = 255, updatable = false)
    private String pickupAddress;

    @Column(name = "destination_lat", nullable = false, updatable = false)
    private double destinationLat;

    @Column(name = "destination_lng", nullable = false, updatable = false)
    private double destinationLng;

    @Column(name = "destination_address", length = 255, updatable = false)
    private String destinationAddress;

    @Column(name = "distance_meters", nullable = false, updatable = false)
    private int distanceMeters;

    /** Fare snapshot when the trip was requested. */
    @Column(name = "fare", nullable = false, updatable = false)
    private long fare;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    private CancelledBy cancelledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "driver_assigned_at")
    private Instant driverAssignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Optimistic locking. Null until the trip is first stored, which tells persistence the trip is new. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Requests a new trip: computes its straight-line distance and fare and starts it as {@link TripStatus#REQUESTED}.
     *
     * @param id          id of the new trip.
     * @param customerId  id of the customer requesting the trip.
     * @param serviceType the service type ordered.
     * @param pickup      where the customer will be picked up.
     * @param destination where the customer wants to go.
     * @param fares       prices the trip from its distance.
     * @param now         when the trip is requested.
     * @return the requested trip, not yet stored.
     * @throws InvalidTripException if pickup and destination are the same place.
     */
    public static Trip request(UUID id, long customerId, ServiceType serviceType, Location pickup,
                               Location destination, FareCalculator fares, Instant now) {
        int distanceMeters = pickup.coordinate().distanceMetersTo(destination.coordinate());
        if (distanceMeters <= 0) {
            throw new InvalidTripException("Pickup and destination must be different places");
        }
        return Trip.builder()
                .id(id)
                .customerId(customerId)
                .serviceTypeId(serviceType.getId())
                .status(TripStatus.REQUESTED)
                .pickupLat(pickup.coordinate().latitude())
                .pickupLng(pickup.coordinate().longitude())
                .pickupAddress(pickup.address())
                .destinationLat(destination.coordinate().latitude())
                .destinationLng(destination.coordinate().longitude())
                .destinationAddress(destination.address())
                .distanceMeters(distanceMeters)
                .fare(fares.calculateFare(distanceMeters))
                .requestedAt(now)
                .build();
    }

    /**
     * Assigns a driver to this trip and moves it to {@link TripStatus#DRIVER_ASSIGNED}.
     *
     * @throws IllegalStateException if the trip is no longer waiting for a driver.
     */
    public void assignDriver(DriverProfile driver, Instant now) {
        if (status != TripStatus.REQUESTED) {
            throw new IllegalStateException("Trip " + id + " cannot be assigned a driver in status " + status);
        }
        this.driverId = driver.getId();
        this.status = TripStatus.DRIVER_ASSIGNED;
        this.driverAssignedAt = now;
    }
}
