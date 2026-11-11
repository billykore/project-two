package org.kore.myapp.adapter.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BcryptPasswordHasherTest {

    // Strength 4 keeps the test fast; the application uses 14 (SecurityConfig).
    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher(new BCryptPasswordEncoder(4));

    @Test
    void hashesToBcryptThatMatchesTheRawPassword() {
        String hash = hasher.hash("secret123");

        assertThat(hash).startsWith("$2").isNotEqualTo("secret123");
        assertThat(hasher.compare("secret123", hash)).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        assertThat(hasher.compare("wrong", hasher.hash("secret123"))).isFalse();
    }
}
