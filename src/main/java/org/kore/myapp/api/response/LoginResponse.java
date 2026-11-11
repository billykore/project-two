package org.kore.myapp.api.response;

import org.kore.myapp.core.valueobject.Token;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        UUID sessionId,
        String accessToken,
        Instant expiresAt
) {
    public static LoginResponse from(Token token) {
        return new LoginResponse(token.sessionId(), token.accessToken(), token.expiresAt());
    }
}
