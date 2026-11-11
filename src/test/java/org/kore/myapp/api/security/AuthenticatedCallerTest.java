package org.kore.myapp.api.security;

import org.junit.jupiter.api.Test;
import org.kore.myapp.core.exception.UnidentifiedCallerException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedCallerTest {

    @Test
    void numericSubjectIsTheUserId() {
        assertThat(AuthenticatedCaller.userId(token().subject("42").build())).isEqualTo(42L);
    }

    @Test
    void missingSubjectIsRejected() {
        Jwt jwt = token().claim("scope", "read").build();

        assertThatThrownBy(() -> AuthenticatedCaller.userId(jwt)).isInstanceOf(UnidentifiedCallerException.class);
    }

    @Test
    void nonNumericSubjectIsRejected() {
        Jwt jwt = token().subject("abc").build();

        assertThatThrownBy(() -> AuthenticatedCaller.userId(jwt)).isInstanceOf(UnidentifiedCallerException.class);
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "HS256");
    }
}
