package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.TripOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface SpringDataTripOfferRepository extends JpaRepository<TripOffer, Long> {

    @Query("""
            select o from TripOffer o
            where o.tripId = :tripId
              and o.driverId = :driverId
              and o.status = OfferStatus.PENDING
            """)
    Optional<TripOffer> findPending(UUID tripId, long driverId);
}
