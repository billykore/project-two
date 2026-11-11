package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.valueobject.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface SpringDataTripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findFirstByCustomerIdAndStatusIn(long customerId, Collection<TripStatus> statuses);
}
