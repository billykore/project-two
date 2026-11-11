package org.kore.myapp.api.security;

import org.kore.myapp.core.exception.UnidentifiedCallerException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the calling user's id from an authenticated request's JWT.
 */
public final class AuthenticatedCaller {

    private AuthenticatedCaller() {
    }

    /**
     * @param jwt the validated token of the request.
     * @return the user id in the token's {@code sub} claim.
     * @throws UnidentifiedCallerException if the token has no {@code sub}, or it isn't a number.
     */
    public static long userId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new UnidentifiedCallerException("Token has no subject");
        }
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new UnidentifiedCallerException("Token subject is not a user id");
        }
    }
}
