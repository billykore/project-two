package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.PasswordResetCode;
import org.kore.myapp.core.exception.PasswordResetBusyException;

import java.time.Instant;
import java.util.Optional;

/**
 * Finds and stores {@link PasswordResetCode}s.
 */
public interface PasswordResetCodeRepository {

    /**
     * Inserts a new code and checks database constraints immediately.
     *
     * @param code the code to store.
     * @return the stored code, with its id.
     * @throws PasswordResetBusyException if the account already has an open code (a concurrent issue that slipped
     *                                    past the account lock).
     */
    PasswordResetCode add(PasswordResetCode code);

    /**
     * Stores changes to a code loaded from this repository.
     *
     * @param code the changed code.
     * @return the stored code.
     */
    PasswordResetCode update(PasswordResetCode code);

    /**
     * @param userId the account.
     * @return the account's code that is neither used nor invalidated (it may be expired), or empty if there is
     * none.
     */
    Optional<PasswordResetCode> findOpen(long userId);

    /**
     * @param userId the account.
     * @param since  the start of the period, inclusive.
     * @return how many codes were issued to the account at or after {@code since}.
     */
    long countIssuedSince(long userId, Instant since);
}
