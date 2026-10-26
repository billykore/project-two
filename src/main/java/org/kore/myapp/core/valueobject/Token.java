package org.kore.myapp.core.valueobject;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Token generates by token issuer.
 */
@Builder
@Getter
@Setter
public class Token {

    private UUID sessionId;

    private String accessToken;

    private Instant expiresAt;
}
