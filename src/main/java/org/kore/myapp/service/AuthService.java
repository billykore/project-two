package org.kore.myapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kore.myapp.core.entity.Session;
import org.kore.myapp.core.entity.User;
import org.kore.myapp.core.exception.InvalidCredentialsException;
import org.kore.myapp.core.port.*;
import org.kore.myapp.core.valueobject.Token;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthUseCase {

    private final UserRepository userRepository;

    private final SessionRepository sessionRepository;

    private final TokenIssuer tokenIssuer;

    private final PasswordHasher passwordHasher;

    private final Clock clock;

    @Override
    @Transactional
    public Token login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .filter(u -> passwordHasher.compare(password, u.getPassword()))
                .orElseThrow(() -> {
                    log.warn("findByUsername failed, username={}", username);
                    return new InvalidCredentialsException();
                });
        Token token = tokenIssuer.issue(user);
        Session session = Session.builder()
                .userId(user.getId())
                .publicId(token.getSessionId())
                .tokenHash(GeneralHasher.sha256Hex(token.getAccessToken()))
                .expiredAt(token.getExpiresAt())
                .createdAt(clock.instant())
                .build();
        sessionRepository.save(session);
        return token;
    }
}
