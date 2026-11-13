package org.kore.myapp.adapter.persistence;

import jakarta.persistence.EntityManager;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.kore.myapp.core.exception.RegistrationConflictException;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.Contact;
import org.kore.myapp.core.valueobject.RegistrationField;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

/**
 * {@link UserRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaUserRepository implements UserRepository {

    /** Unique constraint on {@code users.username}, named by PostgreSQL for the inline UNIQUE in migration V1. */
    static final String USERNAME_CONSTRAINT = "users_username_key";

    /** Unique index on {@code users.email}, see migration V11. */
    static final String EMAIL_CONSTRAINT = "uq_users_email";

    /** Unique index on {@code users.phone}, see migration V11. */
    static final String PHONE_CONSTRAINT = "uq_users_phone";

    private final SpringDataUserRepository users;

    private final EntityManager entityManager;

    private final PasswordResetLockProperties lockProperties;

    JpaUserRepository(SpringDataUserRepository users, EntityManager entityManager,
                      PasswordResetLockProperties lockProperties) {
        this.users = users;
        this.entityManager = entityManager;
        this.lockProperties = lockProperties;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return users.findByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return users.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.existsByEmail(email);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return users.existsByPhone(phone);
    }

    /**
     * Inserts and flushes the user, so database constraints are checked here. If a concurrent registration took the
     * same username, email, or phone in the meantime, the matching unique constraint rejects the insert and this
     * reports it as {@link RegistrationConflictException}. Other integrity violations are not business conflicts and
     * propagate unchanged.
     */
    @Override
    public User add(User user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            RegistrationField field = conflictingField(e);
            if (field != null) {
                throw new RegistrationConflictException(Set.of(field));
            }
            throw e;
        }
    }

    /**
     * Locks the account row with {@code FOR NO KEY UPDATE}, so other password-reset operations on it wait. Before
     * locking, sets PostgreSQL's {@code lock_timeout} for this transaction only (the same as {@code SET LOCAL}), so
     * the wait is bounded by {@code app.password-reset.lock-timeout}. The JPA lock-timeout hint is not used because
     * Hibernate's PostgreSQL dialect may ignore a positive timeout. A timeout is reported as
     * {@link PasswordResetBusyException}.
     */
    @Override
    public Optional<User> findForPasswordReset(Contact contact) {
        entityManager.createNativeQuery("select set_config('lock_timeout', ?1, true)")
                .setParameter(1, lockProperties.lockTimeout().toMillis() + "ms")
                .getSingleResult();
        try {
            return switch (contact.channel()) {
                case EMAIL -> users.findLockedByEmail(contact.address());
                case PHONE -> users.findLockedByPhone(contact.address());
            };
        } catch (PessimisticLockingFailureException e) {
            throw new PasswordResetBusyException(e);
        }
    }

    @Override
    public User update(User user) {
        return users.save(user);
    }

    private static RegistrationField conflictingField(DataIntegrityViolationException e) {
        String constraint = IntegrityViolations.constraintName(e);
        if (USERNAME_CONSTRAINT.equalsIgnoreCase(constraint)) {
            return RegistrationField.USERNAME;
        }
        if (EMAIL_CONSTRAINT.equalsIgnoreCase(constraint)) {
            return RegistrationField.EMAIL;
        }
        if (PHONE_CONSTRAINT.equalsIgnoreCase(constraint)) {
            return RegistrationField.PHONE;
        }
        return null;
    }
}
