package org.kore.myapp.core.port;

import org.kore.myapp.core.valueobject.Token;
import org.kore.myapp.core.exception.InvalidCredentialsException;
import org.kore.myapp.core.exception.TokenIssuanceException;

/**
 * Business logic for authentication operations.
 */
public interface AuthUseCase {
    /**
     * Handle user login.
     *
     * @param username a valid username for login.
     * @param password a valid password for login.
     * @return authorization {@link Token}.
     * @throws InvalidCredentialsException if username is not found or password is not valid.
     * @throws TokenIssuanceException if token is failed to generate.
     */
    Token login(String username, String password);
}
