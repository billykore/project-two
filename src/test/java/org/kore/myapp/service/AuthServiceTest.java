package org.kore.myapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.entity.Session;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.InvalidCredentialsException;
import org.kore.myapp.core.port.PasswordHasher;
import org.kore.myapp.core.port.SessionRepository;
import org.kore.myapp.core.port.TokenHasher;
import org.kore.myapp.core.port.TokenIssuer;
import org.kore.myapp.core.port.UserRepository;
import org.kore.myapp.core.valueobject.Token;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kore.myapp.service.support.Fixtures.NOW;
import static org.kore.myapp.service.support.Fixtures.fixedClock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private UserRepository users;
    private SessionRepository sessions;
    private TokenIssuer tokenIssuer;
    private PasswordHasher passwordHasher;
    private TokenHasher tokenHasher;
    private AuthService authService;
    private User budi;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        sessions = mock(SessionRepository.class);
        tokenIssuer = mock(TokenIssuer.class);
        passwordHasher = mock(PasswordHasher.class);
        tokenHasher = mock(TokenHasher.class);
        authService = new AuthService(users, sessions, tokenIssuer, passwordHasher, tokenHasher, fixedClock());

        budi = User.builder().id(5L).username("budi").password("bcrypt-hash").build();
        when(users.findByUsername("budi")).thenReturn(Optional.of(budi));
        when(passwordHasher.compare("secret123", "bcrypt-hash")).thenReturn(true);
    }

    @Test
    void loginStoresSessionWithHashedTokenAndInjectedTime() {
        Token token = Token.builder().sessionId(SESSION_ID).accessToken("jwt-value")
                .expiresAt(NOW.plusSeconds(10)).build();
        when(tokenIssuer.issue(budi)).thenReturn(token);
        when(tokenHasher.hash("jwt-value")).thenReturn("hashed");

        Token result = authService.login("budi", "secret123");

        assertThat(result).isSameAs(token);
        ArgumentCaptor<Session> stored = ArgumentCaptor.forClass(Session.class);
        verify(sessions).add(stored.capture());
        assertThat(stored.getValue().getUserId()).isEqualTo(5L);
        assertThat(stored.getValue().getPublicId()).isEqualTo(SESSION_ID);
        assertThat(stored.getValue().getTokenHash()).isEqualTo("hashed");
        assertThat(stored.getValue().getExpiredAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(stored.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void loginRejectsUnknownUsername() {
        assertThatThrownBy(() -> authService.login("nobody", "secret123"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(sessions, never()).add(any());
    }

    @Test
    void loginRejectsWrongPasswordWithoutStoringASession() {
        assertThatThrownBy(() -> authService.login("budi", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenIssuer, never()).issue(any());
        verify(sessions, never()).add(any());
    }
}
