package org.kore.myapp.adapter.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.TokenIssuanceException;
import org.kore.myapp.core.valueobject.Token;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issues real HS256 tokens with Nimbus, the same library the application uses, and decodes them again.
 */
class JwtTokenIssuerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final SecretKey KEY = new SecretKeySpec(
            "test-only-hmac-key-never-use-in-production".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private static final JwtProperties PROPS = new JwtProperties("unused", "myapp", Duration.ofMinutes(10));
    private static final User BUDI = User.builder().id(5L).username("budi").build();

    @Test
    void issuesASignedTokenForTheUser() {
        JwtTokenIssuer issuer = new JwtTokenIssuer(
                new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), PROPS, Clock.fixed(NOW, ZoneOffset.UTC));

        Token token = issuer.issue(BUDI);

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        Jwt jwt = decoder().decode(token.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("5");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("myapp");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(token.sessionId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("USER"));
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void reportsAnEncodingFailureAsTokenIssuanceException() {
        JwtEncoder failing = mock(JwtEncoder.class);
        when(failing.encode(any())).thenThrow(new JwtEncodingException("bad key"));
        JwtTokenIssuer issuer = new JwtTokenIssuer(failing, PROPS, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> issuer.issue(BUDI)).isInstanceOf(TokenIssuanceException.class);
    }

    private static NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
        // Only the issuer is checked: the token was issued at a fixed time, so expiry is asserted explicitly above.
        decoder.setJwtValidator(new JwtIssuerValidator("myapp"));
        return decoder;
    }
}
