package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.valueobject.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Trip} related persistence operations.
 * <p>
 * Inherits the standard CRUD operations from {@link JpaRepository}.
 */
public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findFirstByCustomerIdAndStatusIn(long customerId, Collection<TripStatus> statuses);

    default Optional<Trip> findActiveByCustomerId(long customerId) {
        return findFirstByCustomerIdAndStatusIn(customerId, TripStatus.activeStatuses());
    }
}
