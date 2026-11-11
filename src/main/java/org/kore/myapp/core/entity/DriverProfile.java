package org.kore.myapp.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.kore.myapp.core.valueobject.AvailabilityStatus;
import org.kore.myapp.core.valueobject.VerificationStatus;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "driver_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DriverProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private long userId;

    @Column(name = "license_number", nullable = false, length = 30)
    private String licenseNumber;

    @Column(name = "license_expires_at", nullable = false)
    private LocalDate licenseExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 20)
    private AvailabilityStatus availabilityStatus;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Version
    @Builder.Default
    private long version = 0;

    public void startTrip() {
        this.availabilityStatus = AvailabilityStatus.ON_TRIP;
    }
}
