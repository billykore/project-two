package org.kore.myapp.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.kore.myapp.core.domainservice.FareCalculator;
import org.kore.myapp.core.entity.ServiceType;
import org.kore.myapp.core.entity.Trip;
import org.kore.myapp.core.valueobject.Coordinate;
import org.kore.myapp.core.valueobject.Location;
import org.kore.myapp.core.valueobject.OfferStatus;
import org.kore.myapp.core.valueobject.TripStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Inserts and changes rows with plain SQL, so persistence tests can set up states the application can't reach yet
 * (for example trips in {@code ON_TRIP}) without going through the adapter under test.
 */
class PersistenceFixtures {

    static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    static final Location MONAS = new Location(new Coordinate(-6.1754, 106.8272), "Monas");
    static final Location SENAYAN = new Location(new Coordinate(-6.2183, 106.8022), "Senayan");
    static final FareCalculator FARES = new FareCalculator(30, 10_000);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Inserts a user whose username starts with the given prefix and is unique, so rows committed by a
     * non-transactional test never collide with later tests.
     */
    long insertUser(String usernamePrefix) {
        String username = usernamePrefix + "-" + UUID.randomUUID();
        return ((Number) entityManager.createNativeQuery(
                        "insert into users (name, username, password) values (?1, ?2, 'hash') returning id")
                .setParameter(1, "User " + username)
                .setParameter(2, username)
                .getSingleResult()).longValue();
    }

    /**
     * Like {@link #insertUser(String)}, but with the contact details registration requires.
     */
    long insertUser(String usernamePrefix, String email, String phone) {
        String username = usernamePrefix + "-" + UUID.randomUUID();
        return ((Number) entityManager.createNativeQuery(
                        "insert into users (name, username, password, email, phone) "
                                + "values (?1, ?2, 'hash', ?3, ?4) returning id")
                .setParameter(1, "User " + username)
                .setParameter(2, username)
                .setParameter(3, email)
                .setParameter(4, phone)
                .getSingleResult()).longValue();
    }

    String username(long userId) {
        return (String) entityManager.createNativeQuery("select username from users where id = ?1")
                .setParameter(1, userId)
                .getSingleResult();
    }

    long insertDriver(long userId) {
        return ((Number) entityManager.createNativeQuery("""
                        insert into driver_profiles
                            (user_id, license_number, license_expires_at, verification_status, availability_status)
                        values (?1, ?2, date '2030-01-01', 'APPROVED', 'ONLINE')
                        returning id""")
                .setParameter(1, userId)
                .setParameter(2, "LIC-" + userId)
                .getSingleResult()).longValue();
    }

    ServiceType myRide() {
        return entityManager.find(ServiceType.class, 1L);
    }

    Trip newTrip(long customerId) {
        return Trip.request(UUID.randomUUID(), customerId, myRide(), MONAS, SENAYAN, FARES, NOW);
    }

    void setTripStatus(UUID tripId, TripStatus status, Long driverId) {
        entityManager.createNativeQuery("""
                        update trips
                        set status = ?2, driver_id = ?3,
                            cancelled_at = case when ?2 = 'CANCELLED' then now() end
                        where id = ?1""")
                .setParameter(1, tripId)
                .setParameter(2, status.name())
                .setParameter(3, driverId)
                .executeUpdate();
    }

    void insertOffer(UUID tripId, long driverId, OfferStatus status, Instant expiresAt) {
        entityManager.createNativeQuery("""
                        insert into trip_offers (trip_id, driver_id, status, expires_at, responded_at)
                        values (?1, ?2, ?3, ?4, case when ?3 = 'PENDING' then null else now() end)""")
                .setParameter(1, tripId)
                .setParameter(2, driverId)
                .setParameter(3, status.name())
                .setParameter(4, Timestamp.from(expiresAt))
                .executeUpdate();
    }

    /**
     * Inserts a reset code issued at the given time: open when {@code open} is true, otherwise already used.
     */
    void insertResetCode(long userId, Instant issuedAt, boolean open) {
        entityManager.createNativeQuery("""
                        insert into password_reset_codes
                            (user_id, code_hash, channel, issued_at, expires_at, used_at)
                        values (?1, ?2, 'EMAIL', ?3, ?4, ?5)""")
                .setParameter(1, userId)
                .setParameter(2, "a".repeat(64))
                .setParameter(3, Timestamp.from(issuedAt))
                .setParameter(4, Timestamp.from(issuedAt.plusSeconds(300)))
                .setParameter(5, open ? null : Timestamp.from(issuedAt.plusSeconds(1)))
                .executeUpdate();
    }

    /** Removes a customer committed by a non-transactional test, together with their trips. */
    void deleteCustomer(long customerId) {
        entityManager.createNativeQuery("delete from trips where customer_id = ?1")
                .setParameter(1, customerId).executeUpdate();
        entityManager.createNativeQuery("delete from users where id = ?1")
                .setParameter(1, customerId).executeUpdate();
    }

    void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    <T> T find(Class<T> type, Object id) {
        return entityManager.find(type, id);
    }
}
