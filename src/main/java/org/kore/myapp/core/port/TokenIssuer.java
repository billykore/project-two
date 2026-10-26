package org.kore.myapp.core.port;

import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.valueobject.Token;
import org.kore.myapp.core.exception.TokenIssuanceException;

/**
 * Authorization token issuer.
 */
public interface TokenIssuer {
    /**
     * Issuing an authorization token for a user.
     *
     * @param user authenticated user.
     * @return new {@link Token}.
     * @throws TokenIssuanceException if token is failed to generate.
     */
    Token issue(User user);
}
