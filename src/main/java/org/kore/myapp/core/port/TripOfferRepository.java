package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.TripOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link TripOffer} entities.
 */
public interface TripOfferRepository extends JpaRepository<TripOffer, Long> {

    /**
     * Find the pending offer of a trip for a specific driver.
     *
     * @param tripId   the id of the trip being offered.
     * @param driverId the id of the driver who received the offer.
     * @return the pending {@link TripOffer}, or {@link Optional#empty()} if none exists.
     */
    @Query("""
            select o from TripOffer o
            where o.tripId = :tripId
              and o.driverId = :driverId
              and o.status = OfferStatus.PENDING
            """)
    Optional<TripOffer> findPending(UUID tripId, Long driverId);
}
