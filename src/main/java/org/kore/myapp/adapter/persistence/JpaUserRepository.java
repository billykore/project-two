package org.kore.myapp.adapter.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.RegistrationConflictException;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.RegistrationField;
import org.springframework.dao.DataIntegrityViolationException;
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

    JpaUserRepository(SpringDataUserRepository users) {
        this.users = users;
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

    private static RegistrationField conflictingField(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException cve) {
                String constraint = cve.getConstraintName();
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
        return null;
    }
}
