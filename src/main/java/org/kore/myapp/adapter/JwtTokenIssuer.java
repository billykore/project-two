package org.kore.myapp.adapter;

import lombok.RequiredArgsConstructor;
import org.kore.myapp.core.valueobject.Token;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.TokenIssuanceException;
import org.kore.myapp.core.port.TokenIssuer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class JwtTokenIssuer implements TokenIssuer {

    private static final JwsHeader HEADER = JwsHeader.with(MacAlgorithm.HS256).build();

    private final JwtEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    @Override
    public Token issue(User user) {
        UUID sessionId = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("USER"))
                .claim("sid", sessionId.toString())
                .build();

        try {
            String value = encoder.encode(JwtEncoderParameters.from(HEADER, claims)).getTokenValue();
            return Token.builder()
                    .sessionId(sessionId)
                    .accessToken(value)
                    .expiresAt(expiresAt)
                    .build();
        } catch (JwtEncodingException ex) {
            throw new TokenIssuanceException("Failed to generate token " + user.getId(), ex);
        }
    }
}
