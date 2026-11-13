package org.kore.myapp.adapter.persistence;

import org.kore.myapp.core.entity.PasswordResetCode;
import org.kore.myapp.core.exception.PasswordResetBusyException;
import org.kore.myapp.core.port.PasswordResetCodeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * {@link PasswordResetCodeRepository} backed by Spring Data JPA.
 */
@Repository
public class JpaPasswordResetCodeRepository implements PasswordResetCodeRepository {

    /** Partial unique index allowing one open code per account, see migration V12. */
    static final String OPEN_CODE_CONSTRAINT = "uq_password_reset_codes_open";

    private final SpringDataPasswordResetCodeRepository codes;

    JpaPasswordResetCodeRepository(SpringDataPasswordResetCodeRepository codes) {
        this.codes = codes;
    }

    /**
     * Inserts and flushes the code, so database constraints are checked here. A second open code for the same
     * account is reported as {@link PasswordResetBusyException}. Any other integrity violation is a bug, not a
     * business outcome, and is reported as {@link IllegalStateException} naming the constraint.
     */
    @Override
    public PasswordResetCode add(PasswordResetCode code) {
        try {
            return codes.saveAndFlush(code);
        } catch (DataIntegrityViolationException e) {
            if (OPEN_CODE_CONSTRAINT.equalsIgnoreCase(IntegrityViolations.constraintName(e))) {
                throw new PasswordResetBusyException(e);
            }
            throw IntegrityViolations.unexpected("Password reset code", e);
        }
    }

    /**
     * Saves and flushes, so a change that closes an open code reaches the database before a new open code is
     * inserted; otherwise Hibernate would run the insert first and the open-code index would reject it. An
     * integrity violation here is a bug and is reported as {@link IllegalStateException} naming the constraint.
     */
    @Override
    public PasswordResetCode update(PasswordResetCode code) {
        try {
            return codes.saveAndFlush(code);
        } catch (DataIntegrityViolationException e) {
            throw IntegrityViolations.unexpected("Password reset code", e);
        }
    }

    @Override
    public Optional<PasswordResetCode> findOpen(long userId) {
        return codes.findOpen(userId);
    }

    @Override
    public long countIssuedSince(long userId, Instant since) {
        return codes.countByUserIdAndIssuedAtGreaterThanEqual(userId, since);
    }
}
