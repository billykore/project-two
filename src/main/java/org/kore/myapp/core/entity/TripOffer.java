package org.kore.myapp.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.kore.myapp.core.valueobject.OfferStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_offers")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TripOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tripId;

    @Column(nullable = false)
    private long driverId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    @Column
    private Instant respondedAt; // null while the offer is PENDING

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void accept(Instant now) {
        this.respondedAt = now;
        this.status = OfferStatus.ACCEPTED;
    }

    public void reject(Instant now) {
        this.respondedAt = now;
        this.status = OfferStatus.REJECTED;
    }
}
