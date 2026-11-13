package org.kore.myapp.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import org.kore.myapp.core.valueobject.Token;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "5b0c6f0e-6f5a-4d6e-9a59-1f1f0f3f4a11") UUID sessionId,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "HS256 JWT to send as `Authorization: Bearer <accessToken>`.",
                example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "2026-11-11T09:00:10Z") Instant expiresAt
) {
    public static LoginResponse from(Token token) {
        return new LoginResponse(token.sessionId(), token.accessToken(), token.expiresAt());
    }
}
