package org.kore.myapp.core.port;

import org.kore.myapp.core.exception.InvalidCredentialsException;

/**
 * Password hasher operations.
 */
public interface PasswordHasher {
    /**
     * Hash a password.
     *
     * @param rawPassword password for user to login.
     * @return hashed string of password.
     */
    String hash(String rawPassword);

    /**
     *  Compare plain password and hashed password.
     *
     * @param rawPassword raw password for login.
     * @param hashedPassword to compare with plain password.
     * @return compare result, true if hashedPassword is valid result of password.
     * @throws InvalidCredentialsException if password is invalid.
     */
    boolean compare(String rawPassword, String hashedPassword);
}
