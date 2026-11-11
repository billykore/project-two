package org.kore.myapp.adapter.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.valueobject.TripStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link TripRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaTripRepository implements TripRepository {

    /** Unique index allowing one unfinished trip per customer, see migration V10. */
    static final String ACTIVE_TRIP_CONSTRAINT = "uq_trips_customer_active";

    private final SpringDataTripRepository trips;

    JpaTripRepository(SpringDataTripRepository trips) {
        this.trips = trips;
    }

    @Override
    public Optional<Trip> findById(UUID id) {
        return trips.findById(id);
    }

    @Override
    public Optional<Trip> findActiveByCustomerId(long customerId) {
        return trips.findFirstByCustomerIdAndStatusIn(customerId, TripStatus.activeStatuses());
    }

    /**
     * Inserts and flushes the trip, so database constraints are checked here. If a concurrent request created another
     * unfinished trip for the same customer in the meantime, the {@value #ACTIVE_TRIP_CONSTRAINT} index rejects the
     * insert and this reports it as {@link ActiveTripExistsException}. Other integrity violations are not business
     * conflicts and propagate unchanged.
     */
    @Override
    public Trip add(Trip trip) {
        try {
            return trips.saveAndFlush(trip);
        } catch (DataIntegrityViolationException e) {
            if (violates(e, ACTIVE_TRIP_CONSTRAINT)) {
                throw new ActiveTripExistsException(trip.getCustomerId(), null);
            }
            throw e;
        }
    }

    @Override
    public Trip update(Trip trip) {
        return trips.save(trip);
    }

    private static boolean violates(DataIntegrityViolationException e, String constraintName) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException cve) {
                return constraintName.equalsIgnoreCase(cve.getConstraintName());
            }
        }
        return false;
    }
}
