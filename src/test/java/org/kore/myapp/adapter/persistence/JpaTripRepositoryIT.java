package org.kore.myapp.adapter.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kore.myapp.core.entity.DriverProfile;
import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.exception.ActiveTripExistsException;
import org.kore.myapp.core.port.TripRepository;
import org.kore.myapp.core.valueobject.TripStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kore.myapp.adapter.persistence.PersistenceFixtures.FARES;
import static org.kore.myapp.adapter.persistence.PersistenceFixtures.MONAS;
import static org.kore.myapp.adapter.persistence.PersistenceFixtures.NOW;
import static org.kore.myapp.adapter.persistence.PersistenceFixtures.SENAYAN;

@PersistenceAdapterTest
class JpaTripRepositoryIT {

    @Autowired
    private TripRepository trips;

    @Autowired
    private PersistenceFixtures db;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long customerId;

    @BeforeEach
    void setUp() {
        customerId = db.insertUser("customer");
    }

    @Test
    void addThenFindByIdRoundTripsEveryColumn() {
        Trip trip = db.newTrip(customerId);

        trips.add(trip);
        db.flushAndClear();

        Trip found = trips.findById(trip.getId()).orElseThrow();
        assertThat(found).usingRecursiveComparison()
                .ignoringFields("version", "createdAt", "updatedAt")
                .isEqualTo(trip);
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void updatePersistsChangesAndIncrementsVersion() {
        Trip trip = trips.add(db.newTrip(customerId));
        long driverId = db.insertDriver(db.insertUser("driver"));
        db.flushAndClear();

        Trip loaded = trips.findById(trip.getId()).orElseThrow();
        loaded.assignDriver(db.find(DriverProfile.class, driverId), NOW);
        trips.update(loaded);
        db.flushAndClear();

        Trip reloaded = trips.findById(trip.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TripStatus.DRIVER_ASSIGNED);
        assertThat(reloaded.getDriverId()).isEqualTo(driverId);
        assertThat(reloaded.getDriverAssignedAt()).isEqualTo(NOW);
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    /** Moved from TripServiceTest#activeTripLookupCoversEveryUnfinishedStatus, now against the real query. */
    @ParameterizedTest
    @EnumSource(value = TripStatus.class, names = {"REQUESTED", "DRIVER_ASSIGNED", "DRIVER_ARRIVED", "ON_TRIP"})
    void findActiveByCustomerIdFindsEveryUnfinishedStatus(TripStatus status) {
        Trip trip = trips.add(db.newTrip(customerId));
        Long driverId = status == TripStatus.REQUESTED ? null : db.insertDriver(db.insertUser("driver"));
        db.setTripStatus(trip.getId(), status, driverId);
        db.flushAndClear();

        assertThat(trips.findActiveByCustomerId(customerId)).map(Trip::getId).contains(trip.getId());
        assertThat(TripStatus.activeStatuses()).containsExactlyInAnyOrder(
                TripStatus.REQUESTED, TripStatus.DRIVER_ASSIGNED, TripStatus.DRIVER_ARRIVED, TripStatus.ON_TRIP);
    }

    @ParameterizedTest
    @EnumSource(value = TripStatus.class, names = {"COMPLETED", "CANCELLED"})
    void findActiveByCustomerIdIgnoresFinishedTrips(TripStatus status) {
        Trip trip = trips.add(db.newTrip(customerId));
        Long driverId = status == TripStatus.CANCELLED ? null : db.insertDriver(db.insertUser("driver"));
        db.setTripStatus(trip.getId(), status, driverId);
        db.flushAndClear();

        assertThat(trips.findActiveByCustomerId(customerId)).isEmpty();
    }

    @Test
    void findActiveByCustomerIdIgnoresOtherCustomers() {
        trips.add(db.newTrip(customerId));
        long otherCustomer = db.insertUser("other");
        db.flushAndClear();

        assertThat(trips.findActiveByCustomerId(otherCustomer)).isEmpty();
    }

    /**
     * Two requests for the same customer pass the service's pre-check at the same time; the partial unique index
     * {@code uq_trips_customer_active} rejects the second insert and the adapter reports it as a domain conflict.
     * Moved from TripServiceTest#createReportsActiveTripWhenConcurrentInsertHitsUniqueIndex, now against PostgreSQL.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAddsForOneCustomerLetExactlyOneSucceed() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long customer = tx.execute(status -> db.insertUser("concurrent"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        List<UUID> stored = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        stored.add(tx.execute(status -> trips.add(db.newTrip(customer)).getId()));
                    } catch (RuntimeException e) {
                        failures.add(e);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(30, TimeUnit.SECONDS);
            }

            assertThat(stored).hasSize(1);
            assertThat(failures).singleElement()
                    .isInstanceOfSatisfying(ActiveTripExistsException.class,
                            e -> assertThat(e.getActiveTripId()).isNull());
        } finally {
            pool.shutdownNow();
            tx.executeWithoutResult(status -> {
                db.deleteCustomer(customer);
                db.deleteCustomer(customerId); // committed by setUp, since this test runs outside a transaction
            });
        }
    }

    /** Moved from TripServiceTest#createRethrowsOtherIntegrityViolations: only the active-trip conflict is translated. */
    @Test
    void addRethrowsOtherIntegrityViolationsUntranslated() {
        ServiceType unknown = ServiceType.builder().id(999L).name("Unknown").build();
        Trip trip = Trip.request(UUID.randomUUID(), customerId, unknown, MONAS, SENAYAN, FARES, NOW);

        assertThatThrownBy(() -> trips.add(trip))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(ActiveTripExistsException.class);
    }
}
