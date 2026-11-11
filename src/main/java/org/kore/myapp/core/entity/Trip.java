package org.kore.myapp.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.kore.myapp.core.valueobject.CancelledBy;
import org.kore.myapp.core.valueobject.TripStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "driver_assigned_at")
    private Instant driverAssignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Optimistic locking. */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private long version = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
