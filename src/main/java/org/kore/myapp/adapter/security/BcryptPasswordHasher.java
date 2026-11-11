package org.kore.myapp.adapter.security;

import lombok.RequiredArgsConstructor;
import org.kore.myapp.core.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BcryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean compare(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
