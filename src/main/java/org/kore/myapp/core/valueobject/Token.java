package org.kore.myapp.core.valueobject;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * An access token issued to a logged-in user.
 *
 * @param sessionId   public id of the login session the token belongs to.
 * @param accessToken the encoded token the client sends back.
 * @param expiresAt   when the token stops being accepted.
 */
@Builder
public record Token(UUID sessionId, String accessToken, Instant expiresAt) {
}
