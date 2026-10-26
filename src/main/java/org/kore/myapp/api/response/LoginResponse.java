package org.kore.myapp.api.response;

import org.kore.myapp.core.valueobject.Token;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record LoginResponse(
        UUID sessionId,
        String accessToken,
        Instant expiresAt
) {
    public static LoginResponse from(Token token) {
        return new LoginResponse(token.getSessionId(), token.getAccessToken(), token.getExpiresAt());
    }
}
